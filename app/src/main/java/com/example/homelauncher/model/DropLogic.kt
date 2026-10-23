package com.example.homelauncher.model

/** What releasing a dragged item over a page or the dock does. */
sealed interface DropAction {
    data class Move(val container: Int, val pageIndex: Int, val x: Int, val y: Int) : DropAction

    /** Dropped onto the centre of another app: both go into a new folder at that app's cell. */
    data class CreateFolder(val targetId: Long) : DropAction

    /** Dropped onto the centre of a folder. */
    data class AddToFolder(val folderId: Long) : DropAction

    /** A widget dropped exactly over another widget of the same size: both become a stack. */
    data class CreateStack(val targetId: Long) : DropAction

    /** A widget dropped exactly over a stack of the same size. */
    data class AddToStack(val stackId: Long) : DropAction

    /** Nowhere to put it (page or dock full, or a large folder over the dock). */
    data object Reject : DropAction
}

/** Drop and folder placement rules. Pure Kotlin so they can be unit-tested. */
object DropLogic {

    /**
     * @param items the items already in the target container/page.
     * @param desired the cell the dragged item's top-left is over.
     * @param fingerCell the cell under the finger.
     * @param centerHit whether the finger is near the centre of [fingerCell] (folder intent)
     *   rather than near its edge (placement intent).
     * @param draggedId the dragged item's id, or null for a new item (from the drawer or a folder).
     */
    fun decide(
        container: Int,
        pageIndex: Int,
        columns: Int,
        rows: Int,
        items: List<PlacedItem>,
        desired: Cell,
        fingerCell: Cell,
        centerHit: Boolean,
        draggedId: Long?,
        draggedType: Int,
        spanX: Int,
        spanY: Int,
    ): DropAction {
        if (container == Container.DOCK && (spanX != 1 || spanY != 1 || draggedType == ItemType.WIDGET || draggedType == ItemType.STACK)) {
            return DropAction.Reject
        }
        val others = items.filter { it.id != draggedId }

        if (centerHit && draggedType == ItemType.APP && spanX == 1 && spanY == 1) {
            val occupant = others.firstOrNull { it.covers(fingerCell.x, fingerCell.y) }
            when (occupant?.type) {
                ItemType.APP -> return DropAction.CreateFolder(occupant.id)
                ItemType.FOLDER -> return DropAction.AddToFolder(occupant.id)
            }
        }

        if (draggedType == ItemType.WIDGET) {
            // Stacking: the widget's outline sits exactly on a widget or stack of its size.
            val occupant = others.firstOrNull { it.x == desired.x && it.y == desired.y && it.spanX == spanX && it.spanY == spanY }
            when (occupant?.type) {
                ItemType.WIDGET -> return DropAction.CreateStack(occupant.id)
                ItemType.STACK -> return DropAction.AddToStack(occupant.id)
            }
        }

        val grid = GridOccupancy(columns, rows)
        for (o in others) grid.mark(o.x, o.y, o.spanX, o.spanY)
        val x = desired.x.coerceIn(0, (columns - spanX).coerceAtLeast(0))
        val y = desired.y.coerceIn(0, (rows - spanY).coerceAtLeast(0))
        val cell = if (grid.isFree(x, y, spanX, spanY)) Cell(x, y) else grid.findNearestFree(x, y, spanX, spanY)
        return cell?.let { DropAction.Move(container, pageIndex, it.x, it.y) } ?: DropAction.Reject
    }

    /**
     * Where a folder at ([x], [y]) can grow to 2x2 on its page: first the four 2x2 blocks that
     * contain its current cell, then the nearest free 2x2 anywhere on the page. Null if none.
     */
    fun largeFolderSpot(
        folderId: Long,
        x: Int,
        y: Int,
        columns: Int,
        rows: Int,
        items: List<PlacedItem>,
    ): Cell? {
        val grid = GridOccupancy(columns, rows)
        for (o in items) if (o.id != folderId) grid.mark(o.x, o.y, o.spanX, o.spanY)
        for ((ax, ay) in listOf(x to y, x - 1 to y, x to y - 1, x - 1 to y - 1)) {
            if (grid.isFree(ax, ay, 2, 2)) return Cell(ax, ay)
        }
        return grid.findNearestFree(x, y, 2, 2)
    }
}
