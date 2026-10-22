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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One visible item on the home screen or dock. */
class HomeItem(val placed: PlacedItem, val app: AppInfo) {
    val id get() = placed.id
}

data class HomeState(
    val pageCount: Int,
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
    val items: List<HomeItem>,
)

/**
 * Owns the home layout. Reads pages and items from Room, reconciles them with installed apps
 * through [LayoutResolver], writes the resolver's corrections back, and publishes [state].
 */
class HomeRepository(
    private val dao: LauncherDao,
    private val settings: SettingsStore,
    private val apps: AppRepository,
    private val defaults: DefaultApps,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<HomeState?>(null)
    val state: StateFlow<HomeState?> = _state.asStateFlow()

    private val writes = Mutex()

    fun start() {
        scope.launch {
            val installed = apps.apps.filterNotNull().first()
            writes.withLock { createDefaultLayoutIfNeeded(installed) }

            combine(dao.pages(), dao.items(), apps.apps.filterNotNull(), settings.settings) { pages, items, appList, s ->
                compute(pages, items, appList, s)
            }.flowOn(Dispatchers.Default).collect { (state, updates) ->
                _state.value = state
                if (updates.isNotEmpty()) applyUpdates(updates)
            }
        }
    }

    private fun compute(
        pages: List<PageEntity>,
        items: List<ItemEntity>,
        appList: List<AppInfo>,
        s: LauncherSettings,
    ): Pair<HomeState, List<ItemUpdate>> {
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
            columns = s.columns,
            rows = s.rows,
            dockSlots = s.dockSlots,
            installed = byKey.keys,
            defaultActivityFor = { pkg, user -> firstActivity[pkg to user] },
            now = System.currentTimeMillis(),
        )
        val visible = resolved.visible.mapNotNull { p -> byKey[p.key]?.let { HomeItem(p, it) } }
        val pageCount = maxOf(pages.size, (visible.maxOfOrNull { if (it.placed.container == Container.DESKTOP) it.placed.pageIndex else 0 } ?: 0) + 1, 1)
        return HomeState(pageCount, s.columns, s.rows, s.dockSlots, visible) to resolved.updates
    }

    private fun applyUpdates(updates: List<ItemUpdate>) {
        scope.launch {
            writes.withLock {
                val maxPage = updates.filterIsInstance<ItemUpdate.Move>().maxOfOrNull { it.pageIndex } ?: 0
                val pageIds = dao.ensurePages(maxPage + 1)
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
        }
    }

    private suspend fun createDefaultLayoutIfNeeded(installed: List<AppInfo>) {
        val s = settings.current()
        if (s.firstRunDone) return
        if (dao.itemCount() == 0) {
            val plan = DefaultLayoutPlanner.plan(
                defaults.dockCandidates(installed),
                defaults.pageCandidates(installed),
                s.dockSlots,
                s.columns,
            )
            val pageId = dao.ensurePages(1).first()
            val dockCells = DefaultLayoutPlanner.dockPositions(plan.dock.size, s.dockSlots)
            val items = plan.dock.mapIndexed { i, k -> entity(k, Container.DOCK, 0, dockCells[i], 0) } +
                plan.firstPageRow.mapIndexed { i, k -> entity(k, Container.DESKTOP, pageId, i, s.rows - 1) }
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
        val (page, cell) = LayoutResolver.findSlotForNewItem(
            current.items.map { it.placed }, current.pageCount, current.columns, current.rows, preferredPage,
        )
        val pageIds = dao.ensurePages(page + 1)
        dao.insertItem(entity(app.key, Container.DESKTOP, pageIds[page], cell.x, cell.y))
        page
    }
}
