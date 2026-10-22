package com.example.homelauncher.data

import com.example.homelauncher.model.AppKey
import com.example.homelauncher.model.Container
import com.example.homelauncher.model.DefaultLayoutPlanner
import com.example.homelauncher.model.ItemUpdate
import com.example.homelauncher.model.LayoutResolver
import com.example.homelauncher.model.PlacedItem
import com.example.homelauncher.model.StoredItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One visible item on the home screen or dock. */
class HomeItem(val placed: PlacedItem, val app: AppInfo) {
    val id get() = placed.id
}

data class HomeState(
    val mode: LayoutMode,
    val pageCount: Int,
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
    val iconSize: IconSize,
    val showHomeLabels: Boolean,
    val showDockLabels: Boolean,
    val items: List<HomeItem>,
) {
    val drawerEnabled get() = mode == LayoutMode.HOME_AND_DRAWER
}

/**
 * Owns the home layout. Each [LayoutMode] has its own database ([daos]); the active mode's pages
 * and items are reconciled with installed apps through [LayoutResolver], corrections are written
 * back, and the result is published as [state].
 *
 * In [LayoutMode.HOME_ONLY] every installed, non-hidden app must be on home: apps that are not
 * are appended after the last used page, and hidden apps are left out of the view (their
 * stored position is kept, so un-hiding puts them back).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRepository(
    private val daos: Map<LayoutMode, LauncherDao>,
    private val settings: SettingsStore,
    private val apps: AppRepository,
    private val defaults: DefaultApps,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<HomeState?>(null)
    val state: StateFlow<HomeState?> = _state.asStateFlow()

    private val writes = Mutex()

    private class Computed(
        val state: HomeState,
        val dao: LauncherDao,
        val updates: List<ItemUpdate>,
        val additions: List<LayoutResolver.NewPlacement>,
    )

    fun start() {
        scope.launch {
            val installed = apps.apps.filterNotNull().first()
            writes.withLock { createDefaultLayoutIfNeeded(installed) }

            settings.settings.map { it.layoutMode }.distinctUntilChanged().flatMapLatest { mode ->
                val dao = daos.getValue(mode)
                flow {
                    writes.withLock { prepare(mode, dao) }
                    emitAll(
                        combine(dao.pages(), dao.items(), apps.apps.filterNotNull(), settings.settings) { pages, items, appList, s ->
                            compute(mode, dao, pages, items, appList, s)
                        },
                    )
                }
            }.flowOn(Dispatchers.Default).collect { c ->
                _state.value = c.state
                if (c.updates.isNotEmpty() || c.additions.isNotEmpty()) applyUpdates(c.dao, c.updates, c.additions)
            }
        }
    }

    /** First use of home-only mode starts from the current home screen; the rest is appended. */
    private suspend fun prepare(mode: LayoutMode, dao: LauncherDao) {
        if (mode == LayoutMode.HOME_ONLY && dao.itemCount() == 0) {
            val source = daos.getValue(LayoutMode.HOME_AND_DRAWER)
            val sourcePages = source.pagesNow()
            val newIds = dao.ensurePages(maxOf(sourcePages.size, 1))
            val pageMap = sourcePages.withIndex().associate { (i, p) -> p.id to newIds[i] }
            val copies = source.itemsNow().mapNotNull { e ->
                if (e.container == Container.DOCK) e.copy(id = 0)
                else pageMap[e.pageId]?.let { e.copy(id = 0, pageId = it) }
            }
            dao.insertItems(copies)
        }
        dao.ensurePages(1)
    }

    private fun compute(
        mode: LayoutMode,
        dao: LauncherDao,
        pages: List<PageEntity>,
        items: List<ItemEntity>,
        appList: List<AppInfo>,
        s: LauncherSettings,
    ): Computed {
        val grid = s.grids.getValue(mode)
        val pageIndex = pages.withIndex().associate { (i, p) -> p.id to i }
        val byKey = appList.associateBy { it.key }
        val firstActivity = HashMap<Pair<String, Long>, AppKey>()
        for (a in appList) firstActivity.putIfAbsent(a.key.packageName to a.key.userSerial, a.key)

        val stored = items.map { e ->
            StoredItem(
                id = e.id,
                container = e.container,
                pageIndex = if (e.container == Container.DOCK) 0 else pageIndex[e.pageId] ?: -1,
                x = e.cellX, y = e.cellY, spanX = e.spanX, spanY = e.spanY,
                key = AppKey(e.packageName, e.className, e.userSerial),
                missingSince = e.missingSince,
            )
        }
        val resolved = LayoutResolver.resolve(
            items = stored,
            pageCount = pages.size,
            columns = grid.columns,
            rows = grid.rows,
            dockSlots = grid.dockSlots,
            installed = byKey.keys,
            defaultActivityFor = { pkg, user -> firstActivity[pkg to user] },
            now = System.currentTimeMillis(),
        )

        val homeOnly = mode == LayoutMode.HOME_ONLY
        val hidden = s.hiddenByMode.getValue(mode)
        val shown = if (homeOnly) resolved.visible.filter { it.key.toString() !in hidden } else resolved.visible
        val visible = shown.mapNotNull { p -> byKey[p.key]?.let { HomeItem(p, it) } }.toMutableList()

        var additions = emptyList<LayoutResolver.NewPlacement>()
        if (homeOnly) {
            val onHome = resolved.visible.mapTo(HashSet()) { it.key }
            val missing = appList.filter { it.key !in onHome && it.key.toString() !in hidden }.map { it.key }
            additions = LayoutResolver.placeMissingApps(resolved.visible, pages.size, grid.columns, grid.rows, missing)
            // Show them right away; the insert's own emission follows a moment later.
            for (a in additions) {
                val placed = PlacedItem(-(visible.size + 1L), Container.DESKTOP, a.pageIndex, a.cell.x, a.cell.y, 1, 1, a.key)
                byKey[a.key]?.let { visible += HomeItem(placed, it) }
            }
        }

        val pageCount = maxOf(
            pages.size,
            (visible.maxOfOrNull { if (it.placed.container == Container.DESKTOP) it.placed.pageIndex else 0 } ?: 0) + 1,
            1,
        )
        val state = HomeState(mode, pageCount, grid.columns, grid.rows, grid.dockSlots, grid.iconSize, grid.showHomeLabels, grid.showDockLabels, visible)
        return Computed(state, dao, resolved.updates, additions)
    }

    private fun applyUpdates(dao: LauncherDao, updates: List<ItemUpdate>, additions: List<LayoutResolver.NewPlacement>) {
        scope.launch {
            writes.withLock {
                val maxPage = maxOf(
                    updates.filterIsInstance<ItemUpdate.Move>().maxOfOrNull { it.pageIndex } ?: 0,
                    additions.maxOfOrNull { it.pageIndex } ?: 0,
                )
                val pageIds = dao.ensurePages(maxPage + 1)
                if (updates.isNotEmpty()) {
                    val deletes = updates.filterIsInstance<ItemUpdate.Delete>().map { it.id }
                    val byId = dao.itemsById(updates.map { it.id }.distinct()).associateBy { it.id }.toMutableMap()
                    for (u in updates) {
                        val e = byId[u.id] ?: continue
                        byId[u.id] = when (u) {
                            is ItemUpdate.MarkMissing -> e.copy(missingSince = u.since)
                            is ItemUpdate.ClearMissing -> e.copy(missingSince = null)
                            is ItemUpdate.Retarget -> e.copy(className = u.key.className)
                            is ItemUpdate.Move -> e.copy(
                                container = u.container,
                                pageId = if (u.container == Container.DOCK) 0 else pageIds[u.pageIndex],
                                cellX = u.x, cellY = u.y,
                            )
                            is ItemUpdate.Delete -> e
                        }
                    }
                    dao.updateItems(byId.values.filter { it.id !in deletes })
                    if (deletes.isNotEmpty()) dao.deleteItems(deletes)
                }
                if (additions.isNotEmpty()) {
                    // Skip apps that another write placed while this one waited for the lock.
                    val present = dao.itemsNow().mapTo(HashSet()) { AppKey(it.packageName, it.className, it.userSerial) }
                    dao.insertItems(
                        additions.filter { it.key !in present }
                            .map { entity(it.key, Container.DESKTOP, pageIds[it.pageIndex], it.cell.x, it.cell.y) },
                    )
                }
            }
        }
    }

    private suspend fun createDefaultLayoutIfNeeded(installed: List<AppInfo>) {
        val s = settings.current()
        if (s.firstRunDone) return
        val dao = daos.getValue(LayoutMode.HOME_AND_DRAWER)
        val grid = s.grids.getValue(LayoutMode.HOME_AND_DRAWER)
        if (dao.itemCount() == 0) {
            val plan = DefaultLayoutPlanner.plan(
                defaults.dockCandidates(installed),
                defaults.pageCandidates(installed),
                grid.dockSlots,
                grid.columns,
            )
            val pageId = dao.ensurePages(1).first()
            val dockCells = DefaultLayoutPlanner.dockPositions(plan.dock.size, grid.dockSlots)
            val items = plan.dock.mapIndexed { i, k -> entity(k, Container.DOCK, 0, dockCells[i], 0) } +
                plan.firstPageRow.mapIndexed { i, k -> entity(k, Container.DESKTOP, pageId, i, grid.rows - 1) }
            dao.insertItems(items)
        } else {
            dao.ensurePages(1)
        }
        settings.setFirstRunDone()
    }

    private fun entity(k: AppKey, container: Int, pageId: Long, x: Int, y: Int) = ItemEntity(
        container = container, pageId = pageId, cellX = x, cellY = y,
        packageName = k.packageName, className = k.className, userSerial = k.userSerial,
    )

    /** Adds [app] to the first free cell, preferring [preferredPage]. Returns the page index used. */
    suspend fun addToHome(app: AppInfo, preferredPage: Int): Int = writes.withLock {
        val current = state.value ?: return@withLock -1
        val dao = daos.getValue(current.mode)
        val (page, cell) = LayoutResolver.findSlotForNewItem(
            current.items.map { it.placed }, current.pageCount, current.columns, current.rows, preferredPage,
        )
        val pageIds = dao.ensurePages(page + 1)
        dao.insertItem(entity(app.key, Container.DESKTOP, pageIds[page], cell.x, cell.y))
        page
    }
}
