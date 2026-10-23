package com.example.homelauncher.data

import android.content.ComponentName
import com.example.homelauncher.model.AppKey
import com.example.homelauncher.model.Cell
import com.example.homelauncher.model.Container
import com.example.homelauncher.model.DefaultLayoutPlanner
import com.example.homelauncher.model.DropAction
import com.example.homelauncher.model.DropLogic
import com.example.homelauncher.model.GridOccupancy
import com.example.homelauncher.model.ItemType
import com.example.homelauncher.model.ItemUpdate
import com.example.homelauncher.model.LayoutResolver
import com.example.homelauncher.model.PlacedItem
import com.example.homelauncher.model.StoredItem
import androidx.room.withTransaction
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

/** Something visible on the home screen or dock. */
sealed class HomeEntry(val placed: PlacedItem) {
    val id get() = placed.id
}

class AppEntry(placed: PlacedItem, val app: AppInfo) : HomeEntry(placed)

class FolderEntry(placed: PlacedItem, val title: String, val color: Int, val apps: List<AppEntry>) : HomeEntry(placed) {
    val isLarge get() = placed.spanX > 1 || placed.spanY > 1
}

/** A widget: the host's [appWidgetId] and the provider it was added from. */
class WidgetEntry(placed: PlacedItem, val appWidgetId: Int, val provider: ComponentName, val userSerial: Long) : HomeEntry(placed)

/** Widgets of one size in one place, swiped through; [widgets] in rank order. */
class StackEntry(placed: PlacedItem, val widgets: List<WidgetEntry>) : HomeEntry(placed)

/** A widget to add: what the user picked, bound to [appWidgetId] by the host. */
class NewWidget(val appWidgetId: Int, val provider: ComponentName, val userSerial: Long, val spanX: Int, val spanY: Int)

data class HomeState(
    val mode: LayoutMode,
    val pageCount: Int,
    val columns: Int,
    val rows: Int,
    val dockSlots: Int,
    val iconSize: IconSize,
    val showHomeLabels: Boolean,
    val showDockLabels: Boolean,
    val entries: List<HomeEntry>,
) {
    val drawerEnabled get() = mode == LayoutMode.HOME_AND_DRAWER
    private val byId: Map<Long, HomeEntry> = entries.associateBy { it.id }

    fun entry(id: Long): HomeEntry? = byId[id]

    /** Top-level items on one page (or the dock). */
    fun placedIn(container: Int, pageIndex: Int): List<PlacedItem> = entries.mapNotNull { e ->
        e.placed.takeIf { it.container == container && (container == Container.DOCK || it.pageIndex == pageIndex) }
    }

    fun folderOf(childId: Long): FolderEntry? =
        entries.firstOrNull { it is FolderEntry && it.apps.any { a -> a.id == childId } } as? FolderEntry

    fun stackOf(childId: Long): StackEntry? =
        entries.firstOrNull { it is StackEntry && it.widgets.any { w -> w.id == childId } } as? StackEntry

    /** A top-level entry or a widget inside a stack. */
    fun widget(id: Long): WidgetEntry? = entry(id) as? WidgetEntry ?: stackOf(id)?.widgets?.firstOrNull { it.id == id }
}

/** What is being dropped: an item already on home (by id) or a new app (from the drawer). */
sealed interface DragItem {
    data class Existing(val id: Long) : DragItem
    data class NewApp(val key: AppKey) : DragItem

    /** A widget from the picker; bound by the host only once it is dropped. */
    data class NewWidget(val provider: ComponentName, val userSerial: Long) : DragItem
}

/**
 * Owns the home layout. Each [LayoutMode] has its own database ([daos]); the active mode's pages
 * and items are reconciled with installed apps through [LayoutResolver], corrections are written
 * back, and the result is published as [state].
 *
 * In [LayoutMode.HOME_ONLY] every installed, non-hidden app must be on home (on a page, in the
 * dock or in a folder): apps that are not are appended after the last used page, and hidden apps
 * are left out of the view (their stored position is kept, so un-hiding puts them back).
 *
 * Every user edit first saves the whole layout of the active mode, so the last edit can be undone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRepository(
    private val dbs: Map<LayoutMode, LauncherDatabase>,
    private val settings: SettingsStore,
    private val apps: AppRepository,
    private val defaults: DefaultApps,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<HomeState?>(null)
    val state: StateFlow<HomeState?> = _state.asStateFlow()

    private val daos: Map<LayoutMode, LauncherDao> = dbs.mapValues { it.value.dao() }
    private val writes = Mutex()

    private class Snapshot(val mode: LayoutMode, val pages: List<PageEntity>, val items: List<ItemEntity>)

    private var undoSnapshot: Snapshot? = null

    private class Computed(
        val state: HomeState,
        val mode: LayoutMode,
        /** The rows the corrections were computed from. */
        val items: List<ItemEntity>,
        val pages: List<PageEntity>,
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
                if (c.updates.isNotEmpty() || c.additions.isNotEmpty()) applyUpdates(c)
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
            val sourceItems = source.itemsNow()
            // Folders first, so their apps can point at the copies' new ids.
            val folderIds = HashMap<Long, Long>()
            for (e in sourceItems.filter { ItemType.isGroup(it.type) }) {
                val copy = if (e.container == Container.DOCK) e.copy(id = 0) else pageMap[e.pageId]?.let { e.copy(id = 0, pageId = it) }
                if (copy != null) folderIds[e.id] = dao.insertItem(copy)
            }
            val rest = sourceItems.filter { !ItemType.isGroup(it.type) }.mapNotNull { e ->
                when (e.container) {
                    Container.DOCK -> e.copy(id = 0)
                    Container.FOLDER, Container.STACK -> folderIds[e.parentId]?.let { e.copy(id = 0, parentId = it) }
                    else -> pageMap[e.pageId]?.let { e.copy(id = 0, pageId = it) }
                }
            }
            dao.insertItems(rest)
        }
        dao.ensurePages(1)
    }

    private fun keyOf(e: ItemEntity): AppKey? =
        if (e.type != ItemEntity.TYPE_APP || e.packageName.isEmpty()) null else AppKey(e.packageName, e.className, e.userSerial)

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
        val entities = items.associateBy { it.id }

        val stored = items.map { e ->
            StoredItem(
                id = e.id,
                container = e.container,
                pageIndex = when (e.container) {
                    Container.DESKTOP -> pageIndex[e.pageId] ?: -1
                    else -> 0
                },
                x = e.cellX, y = e.cellY, spanX = e.spanX, spanY = e.spanY,
                key = keyOf(e),
                missingSince = e.missingSince,
                type = e.type, // ItemEntity.TYPE_* and ItemType share their values.
                parentId = e.parentId,
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
        fun shown(p: PlacedItem) = !homeOnly || p.key?.toString() !in hidden

        fun widgetEntry(p: PlacedItem): WidgetEntry? {
            val e = entities[p.id] ?: return null
            return WidgetEntry(p, e.appWidgetId, ComponentName(e.packageName, e.className), e.userSerial)
        }

        val visible = ArrayList<HomeEntry>(resolved.visible.size)
        for (p in resolved.visible) {
            if (p.type == ItemType.WIDGET) {
                widgetEntry(p)?.let { visible += it }
            } else if (p.type == ItemType.STACK) {
                val widgets = resolved.children[p.id].orEmpty().mapNotNull(::widgetEntry)
                if (widgets.isNotEmpty()) visible += StackEntry(p, widgets)
            } else if (p.type == ItemType.FOLDER) {
                val kids = resolved.children[p.id].orEmpty().filter(::shown).mapNotNull { c -> byKey[c.key]?.let { AppEntry(c, it) } }
                if (kids.isEmpty()) continue
                val e = entities[p.id]
                visible += FolderEntry(p, e?.title.orEmpty(), e?.color ?: 0, kids)
            } else if (shown(p)) {
                byKey[p.key]?.let { visible += AppEntry(p, it) }
            }
        }

        var additions = emptyList<LayoutResolver.NewPlacement>()
        if (homeOnly) {
            val onHome = HashSet<AppKey>()
            resolved.visible.forEach { it.key?.let(onHome::add) }
            resolved.children.values.forEach { list -> list.forEach { c -> c.key?.let(onHome::add) } }
            val missing = appList.filter { it.key !in onHome && it.key.toString() !in hidden }.map { it.key }
            additions = LayoutResolver.placeMissingApps(resolved.visible, pages.size, grid.columns, grid.rows, missing)
            // Show them right away; the insert's own emission follows a moment later.
            for (a in additions) {
                val placed = PlacedItem(-(visible.size + 1L), Container.DESKTOP, a.pageIndex, a.cell.x, a.cell.y, 1, 1, a.key)
                byKey[a.key]?.let { visible += AppEntry(placed, it) }
            }
        }

        val pageCount = maxOf(
            pages.size,
            (visible.maxOfOrNull { if (it.placed.container == Container.DESKTOP) it.placed.pageIndex else 0 } ?: 0) + 1,
            1,
        )
        val state = HomeState(mode, pageCount, grid.columns, grid.rows, grid.dockSlots, grid.iconSize, grid.showHomeLabels, grid.showDockLabels, visible)
        return Computed(state, mode, items, pages, resolved.updates, additions)
    }

    private fun applyUpdates(c: Computed) {
        val updates = c.updates
        val additions = c.additions
        val dao = daos.getValue(c.mode)
        scope.launch {
            writes.withLock {
                dbs.getValue(c.mode).withTransaction { applyIfCurrent(dao, c, updates, additions) }
            }
        }
    }

    /**
     * Corrections are only valid for the rows they were computed from. If an edit landed in the
     * meantime they are dropped: the edit's own emission computes fresh ones.
     */
    private suspend fun applyIfCurrent(
        dao: LauncherDao,
        c: Computed,
        updates: List<ItemUpdate>,
        additions: List<LayoutResolver.NewPlacement>,
    ) {
        if (dao.itemsNow().sortedBy { it.id } != c.items.sortedBy { it.id } || dao.pagesNow() != c.pages) return
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
                    is ItemUpdate.Move -> e.placedAt(u.container, if (u.container == Container.DOCK) 0 else pageIds[u.pageIndex], u.x, u.y)
                    is ItemUpdate.Delete -> e
                }
            }
            dao.updateItems(byId.values.filter { it.id !in deletes })
            if (deletes.isNotEmpty()) dao.deleteItems(deletes)
        }
        if (additions.isNotEmpty()) {
            // Skip apps that another write placed while this one waited for the lock.
            val present = dao.itemsNow().mapNotNullTo(HashSet()) { keyOf(it) }
            dao.insertItems(
                additions.filter { it.key !in present }
                    .map { entity(it.key, Container.DESKTOP, pageIds[it.pageIndex], it.cell.x, it.cell.y) },
            )
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

    private fun ItemEntity.placedAt(container: Int, pageId: Long, x: Int, y: Int) =
        copy(container = container, pageId = pageId, cellX = x, cellY = y, parentId = 0)

    private fun ItemEntity.inFolder(folderId: Long, rank: Int) =
        copy(container = Container.FOLDER, pageId = 0, cellX = rank, cellY = 0, spanX = 1, spanY = 1, parentId = folderId)

    /** In a stack a widget takes the stack's size. */
    private fun ItemEntity.inStack(stack: ItemEntity, rank: Int) =
        copy(container = Container.STACK, pageId = 0, cellX = rank, cellY = 0, spanX = stack.spanX, spanY = stack.spanY, parentId = stack.id)

    // ---------------------------------------------------------------------------------- edits

    /** Runs a user edit on the active mode's database after saving the layout for undo. */
    private suspend fun <T> edit(block: suspend (LauncherDao, HomeState) -> T): T? = writes.withLock {
        val st = state.value ?: return@withLock null
        val dao = daos.getValue(st.mode)
        // One transaction, so observers never see a half-done edit (a folder without its apps).
        dbs.getValue(st.mode).withTransaction {
            undoSnapshot = Snapshot(st.mode, dao.pagesNow(), dao.itemsNow())
            block(dao, st)
        }
    }

    /** Restores the layout from before the last edit. Returns false if there is nothing to undo. */
    suspend fun undo(): Boolean = writes.withLock {
        val snap = undoSnapshot ?: return@withLock false
        undoSnapshot = null
        dbs.getValue(snap.mode).withTransaction { daos.getValue(snap.mode).replaceAll(snap.pages, snap.items) }
        true
    }

    private suspend fun pageId(dao: LauncherDao, container: Int, pageIndex: Int): Long =
        if (container == Container.DOCK) 0 else dao.ensurePages(pageIndex + 1)[pageIndex]

    /** Inserts a new app or detaches an existing item, ready to be placed; returns its entity. */
    private suspend fun materialize(dao: LauncherDao, item: DragItem): ItemEntity? = when (item) {
        is DragItem.Existing -> dao.item(item.id)
        is DragItem.NewApp -> {
            val e = entity(item.key, Container.DESKTOP, 0, 0, 0)
            e.copy(id = dao.insertItem(e))
        }
        is DragItem.NewWidget -> null // Added through addWidget after binding.
    }

    /** Moves an existing item or places a new app at a cell of a page or the dock. */
    suspend fun place(item: DragItem, container: Int, pageIndex: Int, x: Int, y: Int) = edit { dao, _ ->
        val e = materialize(dao, item) ?: return@edit
        val oldParent = e.parentId
        dao.updateItem(e.placedAt(container, pageId(dao, container, pageIndex), x, y))
        if (oldParent != 0L) tidyFolder(dao, oldParent)
    }

    /** Drops [item] onto the app [targetId]: both go into a new folder in the target's cell. */
    suspend fun createFolder(item: DragItem, targetId: Long, defaultTitle: String) = edit { dao, _ ->
        val target = dao.item(targetId) ?: return@edit
        val dragged = materialize(dao, item) ?: return@edit
        val oldParent = dragged.parentId
        val folder = ItemEntity(
            type = ItemEntity.TYPE_FOLDER,
            container = target.container, pageId = target.pageId, cellX = target.cellX, cellY = target.cellY,
            packageName = "", className = "", userSerial = 0, title = defaultTitle,
        )
        val folderId = dao.insertItem(folder)
        dao.updateItems(listOf(target.inFolder(folderId, 0), dragged.inFolder(folderId, 1)))
        if (oldParent != 0L) tidyFolder(dao, oldParent)
    }

    suspend fun addToFolder(item: DragItem, folderId: Long) = edit { dao, _ ->
        val dragged = materialize(dao, item) ?: return@edit
        if (dragged.parentId == folderId) return@edit
        val oldParent = dragged.parentId
        val rank = (dao.children(folderId).maxOfOrNull { it.cellX } ?: -1) + 1
        dao.updateItem(dragged.inFolder(folderId, rank))
        if (oldParent != 0L) tidyFolder(dao, oldParent)
    }

    /**
     * After an item leaves a folder or stack: an empty one is deleted, and one with a single item
     * left is replaced by that item in the same place (an app as 1x1, a widget at the stack's size).
     */
    private suspend fun tidyFolder(dao: LauncherDao, folderId: Long) {
        val folder = dao.item(folderId) ?: return
        val kids = dao.children(folderId)
        val stack = folder.type == ItemEntity.TYPE_STACK
        when (kids.size) {
            0 -> dao.deleteItems(listOf(folderId))
            1 -> {
                val last = kids[0].placedAt(folder.container, folder.pageId, folder.cellX, folder.cellY)
                dao.updateItem(if (stack) last.copy(spanX = folder.spanX, spanY = folder.spanY) else last.copy(spanX = 1, spanY = 1))
                dao.deleteItems(listOf(folderId))
            }
            else -> {
                // Close the gap left in the ranks.
                dao.updateItems(kids.mapIndexed { i, k -> k.copy(cellX = i) })
            }
        }
    }

    /** Removes an item from home (a folder with its apps). */
    suspend fun remove(id: Long) = edit { dao, _ ->
        val e = dao.item(id) ?: return@edit
        dao.deleteItems(dao.children(id).map { it.id } + id)
        if (e.parentId != 0L) tidyFolder(dao, e.parentId)
    }

    /**
     * Puts a folder's apps (or a stack's widgets) back on its page: the first in the group's
     * place, the rest nearby, then on the following pages.
     */
    suspend fun ungroup(folderId: Long) = edit { dao, st ->
        val folder = dao.item(folderId) ?: return@edit
        val kids = dao.children(folderId)
        val page = st.entry(folderId)?.placed?.pageIndex ?: 0
        val others = st.entries.map { it.placed }.filter { it.id != folderId }
        val grid = Array(st.pageCount + kids.size + 1) { GridOccupancy(st.columns, st.rows) }
        val dock = GridOccupancy(st.dockSlots, 1)
        for (o in others) {
            if (o.container == Container.DOCK) dock.mark(o.x, 0) else if (o.pageIndex in grid.indices) grid[o.pageIndex].mark(o.x, o.y, o.spanX, o.spanY)
        }
        val updated = ArrayList<ItemEntity>()
        kids.forEachIndexed { i, kid ->
            val sx = minOf(kid.spanX, st.columns)
            val sy = minOf(kid.spanY, st.rows)
            if (i == 0) {
                updated += kid.placedAt(folder.container, folder.pageId, folder.cellX, folder.cellY).copy(spanX = sx, spanY = sy)
                if (folder.container == Container.DOCK) dock.mark(folder.cellX, 0) else grid[page].mark(folder.cellX, folder.cellY, sx, sy)
            } else {
                var p = if (folder.container == Container.DOCK) 0 else page
                var cell: Cell? = null
                while (cell == null && p < grid.size) {
                    cell = grid[p].findNearestFree(folder.cellX, folder.cellY, sx, sy)
                    if (cell == null) p++
                }
                if (cell == null) return@forEachIndexed // Cannot happen: the last page is empty.
                grid[p].mark(cell.x, cell.y, sx, sy)
                updated += kid.placedAt(Container.DESKTOP, pageId(dao, Container.DESKTOP, p), cell.x, cell.y).copy(spanX = sx, spanY = sy)
            }
        }
        dao.updateItems(updated)
        dao.deleteItems(listOf(folderId))
    }

    /** Drops [item] (a widget) onto the widget [targetId]: both go into a new stack in its place. */
    suspend fun createStack(item: DragItem, targetId: Long) = edit { dao, _ ->
        val target = dao.item(targetId) ?: return@edit
        val dragged = materialize(dao, item) ?: return@edit
        stackWith(dao, target, dragged)
    }

    private suspend fun stackWith(dao: LauncherDao, target: ItemEntity, dragged: ItemEntity) {
        val oldParent = dragged.parentId
        val stack = ItemEntity(
            type = ItemEntity.TYPE_STACK,
            container = target.container, pageId = target.pageId, cellX = target.cellX, cellY = target.cellY,
            spanX = target.spanX, spanY = target.spanY,
            packageName = "", className = "", userSerial = 0,
        )
        val saved = stack.copy(id = dao.insertItem(stack))
        // The dropped widget goes on top.
        dao.updateItems(listOf(dragged.inStack(saved, 0), target.inStack(saved, 1)))
        if (oldParent != 0L) tidyFolder(dao, oldParent)
    }

    suspend fun addToStack(item: DragItem, stackId: Long) = edit { dao, _ ->
        val stack = dao.item(stackId) ?: return@edit
        val dragged = materialize(dao, item) ?: return@edit
        addToStack(dao, stack, dragged)
    }

    private suspend fun addToStack(dao: LauncherDao, stack: ItemEntity, dragged: ItemEntity) {
        if (dragged.parentId == stack.id) return
        val oldParent = dragged.parentId
        // The new widget goes on top; the others shift down one rank.
        val kids = dao.children(stack.id).mapIndexed { i, k -> k.copy(cellX = i + 1) }
        dao.updateItems(kids + dragged.inStack(stack, 0))
        if (oldParent != 0L) tidyFolder(dao, oldParent)
    }

    /** Adds a bound widget where it was dropped: on a cell, or onto a widget or stack. */
    suspend fun addWidget(w: NewWidget, action: DropAction) = edit { dao, _ ->
        val base = ItemEntity(
            type = ItemEntity.TYPE_WIDGET,
            container = Container.DESKTOP, pageId = 0, cellX = 0, cellY = 0, spanX = w.spanX, spanY = w.spanY,
            packageName = w.provider.packageName, className = w.provider.className, userSerial = w.userSerial,
            appWidgetId = w.appWidgetId,
        )
        when (action) {
            is DropAction.Move -> dao.insertItem(base.copy(pageId = pageId(dao, Container.DESKTOP, action.pageIndex), cellX = action.x, cellY = action.y))
            is DropAction.CreateStack -> {
                val target = dao.item(action.targetId) ?: return@edit
                stackWith(dao, target, base.copy(id = dao.insertItem(base)))
            }
            is DropAction.AddToStack -> {
                val stack = dao.item(action.stackId) ?: return@edit
                addToStack(dao, stack, base.copy(id = dao.insertItem(base)))
            }
            else -> Unit
        }
    }

    /** Points a widget at a newly bound host id (after its app was reinstalled). */
    suspend fun rebindWidget(id: Long, appWidgetId: Int) = edit { dao, _ ->
        dao.item(id)?.let { dao.updateItem(it.copy(appWidgetId = appWidgetId)) }
    }

    /** Resizes a widget or stack (and the widgets in it) to a new cell rectangle. */
    suspend fun resize(id: Long, x: Int, y: Int, spanX: Int, spanY: Int) = edit { dao, _ ->
        val e = dao.item(id) ?: return@edit
        dao.updateItem(e.copy(cellX = x, cellY = y, spanX = spanX, spanY = spanY))
        if (e.type == ItemEntity.TYPE_STACK) dao.updateItems(dao.children(id).map { it.copy(spanX = spanX, spanY = spanY) })
    }

    /** Every widget id stored in any mode, so unused host ids can be released. */
    suspend fun widgetIdsInUse(): Set<Int> = writes.withLock {
        daos.values.flatMapTo(HashSet()) { it.widgetIds() }
    }

    /** Makes a folder 2x2 (moving it if needed) or 1x1. Returns false if there is no room. */
    suspend fun setFolderLarge(folderId: Long, large: Boolean): Boolean = edit { dao, st ->
        val folder = dao.item(folderId) ?: return@edit false
        if (!large) {
            dao.updateItem(folder.copy(spanX = 1, spanY = 1))
            return@edit true
        }
        if (folder.container != Container.DESKTOP) return@edit false
        val placed = st.entry(folderId)?.placed ?: return@edit false
        val spot = DropLogic.largeFolderSpot(folderId, placed.x, placed.y, st.columns, st.rows, st.placedIn(Container.DESKTOP, placed.pageIndex))
            ?: return@edit false
        dao.updateItem(folder.copy(cellX = spot.x, cellY = spot.y, spanX = 2, spanY = 2))
        true
    } ?: false

    suspend fun renameFolder(folderId: Long, title: String) = edit { dao, _ ->
        dao.item(folderId)?.let { dao.updateItem(it.copy(title = title.trim())) }
    }

    suspend fun setFolderColor(folderId: Long, color: Int) = edit { dao, _ ->
        dao.item(folderId)?.let { dao.updateItem(it.copy(color = color)) }
    }

    /** Appends an empty page; returns its index. */
    suspend fun addPage(): Int = edit { dao, _ ->
        val n = dao.pagesNow().size
        dao.ensurePages(n + 1)
        n
    } ?: 0

    /** Deletes a page other than the home page, with what is on it (in home-only mode its apps come back on the last page). */
    suspend fun deletePage(pageIndex: Int) = edit { dao, _ ->
        val pages = dao.pagesNow()
        if (pages.size <= 1 || pageIndex !in 1 until pages.size) return@edit // Page 0 is the home page.
        val page = pages[pageIndex]
        val onPage = dao.itemsNow().filter { it.container == Container.DESKTOP && it.pageId == page.id }
        val kids = onPage.filter { it.type == ItemEntity.TYPE_FOLDER }.flatMap { dao.children(it.id) }
        dao.deleteItems(onPage.map { it.id } + kids.map { it.id })
        dao.deletePage(page.id)
        dao.updatePages(dao.pagesNow().mapIndexed { i, p -> p.copy(rank = i) })
    }

    /** Moves the page at [from] to position [to]. */
    suspend fun movePage(from: Int, to: Int) = edit { dao, _ ->
        val pages = dao.pagesNow().toMutableList()
        if (from !in pages.indices || to !in pages.indices || from == to) return@edit
        pages.add(to, pages.removeAt(from))
        dao.updatePages(pages.mapIndexed { i, p -> p.copy(rank = i) })
    }

    /** Adds [app] to the first free cell, preferring [preferredPage]. Returns the page index used. */
    suspend fun addToHome(app: AppInfo, preferredPage: Int): Int = edit { dao, st ->
        val (page, cell) = LayoutResolver.findSlotForNewItem(
            st.entries.map { it.placed }, st.pageCount, st.columns, st.rows, preferredPage,
        )
        dao.insertItem(entity(app.key, Container.DESKTOP, pageId(dao, Container.DESKTOP, page), cell.x, cell.y))
        page
    } ?: -1
}
