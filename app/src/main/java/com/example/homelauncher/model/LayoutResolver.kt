package com.example.homelauncher.model

/** Where a home item lives. */
object Container {
    const val DESKTOP = 0
    const val DOCK = 1
}

/** A stored home item, independent of Room. [key] is the launch target ("pkg/cls#userSerial"). */
data class StoredItem(
    val id: Long,
    val container: Int,
    val pageIndex: Int,
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
    val key: AppKey,
    val missingSince: Long?,
)

/** A launchable activity: package, class and the profile (user serial) it belongs to. */
data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    override fun toString() = "$packageName/$className#$userSerial"

    companion object {
        fun parse(s: String): AppKey? {
            val slash = s.indexOf('/')
            val hash = s.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1) return null
            val serial = s.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(s.substring(0, slash), s.substring(slash + 1, hash), serial)
        }
    }
}

/** Changes the resolver wants written back to storage. */
sealed interface ItemUpdate {
    val id: Long

    data class MarkMissing(override val id: Long, val since: Long) : ItemUpdate
    data class ClearMissing(override val id: Long) : ItemUpdate
    data class Retarget(override val id: Long, val key: AppKey) : ItemUpdate

    /** [pageIndex] may be past the last existing page; storage then appends pages. */
    data class Move(override val id: Long, val container: Int, val pageIndex: Int, val x: Int, val y: Int) : ItemUpdate
    data class Delete(override val id: Long) : ItemUpdate
}

data class PlacedItem(
    val id: Long,
    val container: Int,
    val pageIndex: Int,
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
    val key: AppKey,
)

class ResolvedLayout(val visible: List<PlacedItem>, val updates: List<ItemUpdate>)

/**
 * Decides what the home screen shows given what is stored and what is installed.
 *
 * - An item whose exact activity is gone but whose package still has a launchable activity is
 *   retargeted to it (apps sometimes rename their launcher activity in an update).
 * - An item whose package is gone is hidden, not deleted, and remembers when it went missing.
 *   If the app comes back within [ghostRetentionMs] (a reinstall, an SD card being remounted)
 *   the icon returns to its old place. After that it is deleted.
 * - Items that no longer fit (grid made smaller, cell taken by something added meanwhile) move
 *   to the first free cell on their page, then the next pages, then a new page.
 */
object LayoutResolver {

    const val DEFAULT_GHOST_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    fun resolve(
        items: List<StoredItem>,
        pageCount: Int,
        columns: Int,
        rows: Int,
        dockSlots: Int,
        installed: Set<AppKey>,
        defaultActivityFor: (packageName: String, userSerial: Long) -> AppKey?,
        now: Long,
        ghostRetentionMs: Long = DEFAULT_GHOST_RETENTION_MS,
    ): ResolvedLayout {
        val updates = ArrayList<ItemUpdate>()
        val present = ArrayList<StoredItem>(items.size)

        for (item in items) {
            val target = when {
                item.key in installed -> item.key
                else -> defaultActivityFor(item.key.packageName, item.key.userSerial)
            }
            if (target == null) {
                when {
                    item.missingSince == null -> updates += ItemUpdate.MarkMissing(item.id, now)
                    now - item.missingSince > ghostRetentionMs -> updates += ItemUpdate.Delete(item.id)
                }
                continue
            }
            if (target != item.key) updates += ItemUpdate.Retarget(item.id, target)
            if (item.missingSince != null) updates += ItemUpdate.ClearMissing(item.id)
            present += item.copy(key = target)
        }

        // Items that never went missing keep their cells; restored ghosts yield to them.
        val ordered = present.sortedWith(compareBy<StoredItem>({ it.missingSince != null }, { it.id }))

        val pages = ArrayList<GridOccupancy>()
        fun page(i: Int): GridOccupancy {
            while (pages.size <= i) pages += GridOccupancy(columns, rows)
            return pages[i]
        }
        repeat(maxOf(pageCount, 1)) { page(it) }
        val dock = GridOccupancy(dockSlots, 1)

        val visible = ArrayList<PlacedItem>(ordered.size)
        val displaced = ArrayList<StoredItem>()

        for (item in ordered) {
            val fitsDock = item.container == Container.DOCK && dock.isFree(item.x, 0)
            val fitsPage = item.container == Container.DESKTOP && item.pageIndex in 0 until pageCount &&
                page(item.pageIndex).isFree(item.x, item.y, item.spanX, item.spanY)
            when {
                fitsDock -> {
                    dock.mark(item.x, 0)
                    visible += item.placed(Container.DOCK, 0, item.x, 0)
                }
                fitsPage -> {
                    page(item.pageIndex).mark(item.x, item.y, item.spanX, item.spanY)
                    visible += item.placed(Container.DESKTOP, item.pageIndex, item.x, item.y)
                }
                else -> displaced += item
            }
        }

        for (item in displaced) {
            // Prefer the item's own page, then pages in order, then a fresh page.
            val start = if (item.container == Container.DESKTOP) item.pageIndex.coerceIn(0, pages.size - 1) else 0
            val order = listOf(start) + pages.indices.filter { it != start }
            var placed = false
            for (p in order) {
                val cell = pages[p].findFirstFree(item.spanX, item.spanY) ?: continue
                pages[p].mark(cell.x, cell.y, item.spanX, item.spanY)
                visible += item.placed(Container.DESKTOP, p, cell.x, cell.y)
                updates += ItemUpdate.Move(item.id, Container.DESKTOP, p, cell.x, cell.y)
                placed = true
                break
            }
            if (!placed) {
                val p = pages.size
                val cell = page(p).findFirstFree(item.spanX, item.spanY)
                if (cell == null) {
                    // Larger than an empty page (only possible after shrinking the grid).
                    updates += ItemUpdate.Delete(item.id)
                } else {
                    pages[p].mark(cell.x, cell.y, item.spanX, item.spanY)
                    visible += item.placed(Container.DESKTOP, p, cell.x, cell.y)
                    updates += ItemUpdate.Move(item.id, Container.DESKTOP, p, cell.x, cell.y)
                }
            }
        }

        return ResolvedLayout(visible, updates)
    }

    private fun StoredItem.placed(container: Int, page: Int, x: Int, y: Int) =
        PlacedItem(id, container, page, x, y, spanX, spanY, key)

    /**
     * Where to put a new 1x1 app: the first free cell on [preferredPage], then on any page,
     * else on a new page (returned index == page count).
     */
    fun findSlotForNewItem(
        visible: List<PlacedItem>,
        pageCount: Int,
        columns: Int,
        rows: Int,
        preferredPage: Int,
    ): Pair<Int, Cell> {
        val pages = List(maxOf(pageCount, 1)) { GridOccupancy(columns, rows) }
        for (v in visible) {
            if (v.container == Container.DESKTOP && v.pageIndex in pages.indices) {
                pages[v.pageIndex].mark(v.x, v.y, v.spanX, v.spanY)
            }
        }
        val start = preferredPage.coerceIn(0, pages.size - 1)
        for (p in listOf(start) + pages.indices.filter { it != start }) {
            pages[p].findFirstFree()?.let { return p to it }
        }
        return pages.size to Cell(0, 0)
    }
}
