package com.example.homelauncher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropLogicTest {

    private fun app(id: Long, x: Int, y: Int) = PlacedItem(id, Container.DESKTOP, 0, x, y, 1, 1, AppKey("p$id", "c", 0))
    private fun folder(id: Long, x: Int, y: Int, span: Int = 1) =
        PlacedItem(id, Container.DESKTOP, 0, x, y, span, span, null, ItemType.FOLDER)

    private fun decide(
        items: List<PlacedItem>,
        desired: Cell,
        center: Boolean = false,
        dragged: Long? = 99,
        type: Int = ItemType.APP,
        span: Int = 1,
        container: Int = Container.DESKTOP,
        columns: Int = 4,
        rows: Int = 6,
    ) = DropLogic.decide(container, 0, columns, rows, items, desired, desired, center, dragged, type, span, span)

    @Test fun freeCellIsUsedAsIs() {
        assertEquals(DropAction.Move(Container.DESKTOP, 0, 2, 3), decide(listOf(app(1, 0, 0)), Cell(2, 3)))
    }

    @Test fun occupiedCellEdgeDropSnapsToNearestFree() {
        val items = listOf(app(1, 1, 1))
        val result = decide(items, Cell(1, 1)) as DropAction.Move
        // One of the four neighbours, never the occupied cell.
        assertEquals(1, kotlin.math.abs(result.x - 1) + kotlin.math.abs(result.y - 1))
    }

    @Test fun centreDropOnAppCreatesFolder() {
        assertEquals(DropAction.CreateFolder(1), decide(listOf(app(1, 2, 2)), Cell(2, 2), center = true))
    }

    @Test fun centreDropOnFolderAddsToIt() {
        assertEquals(DropAction.AddToFolder(5), decide(listOf(folder(5, 0, 0, span = 2)), Cell(1, 1), center = true))
    }

    @Test fun foldersDoNotNest() {
        val result = decide(listOf(folder(5, 0, 0)), Cell(0, 0), center = true, type = ItemType.FOLDER)
        assert(result is DropAction.Move)
    }

    @Test fun droppingOnItsOwnCellKeepsIt() {
        assertEquals(DropAction.Move(Container.DESKTOP, 0, 1, 1), decide(listOf(app(99, 1, 1)), Cell(1, 1), center = true, dragged = 99))
    }

    @Test fun spanIsClampedInsideTheGrid() {
        assertEquals(DropAction.Move(Container.DESKTOP, 0, 2, 4), decide(emptyList(), Cell(3, 5), span = 2))
    }

    @Test fun fullPageRejects() {
        val items = (0 until 4).map { app(it.toLong(), it % 2, it / 2) }
        assertEquals(DropAction.Reject, decide(items, Cell(0, 0), columns = 2, rows = 2))
    }

    @Test fun largeFolderCannotGoInDock() {
        assertEquals(DropAction.Reject, decide(emptyList(), Cell(0, 0), type = ItemType.FOLDER, span = 2, container = Container.DOCK, rows = 1))
    }

    @Test fun largeFolderGrowsAroundItsCellWhenPossible() {
        // Folder at (3,0) on a 4x6 grid: (3,0) cannot start a 2x2, (2,0) can.
        assertEquals(Cell(2, 0), DropLogic.largeFolderSpot(7, 3, 0, 4, 6, listOf(folder(7, 3, 0))))
    }

    @Test fun largeFolderMovesToNearestFreeBlockOrFails() {
        val blockers = listOf(app(1, 0, 0), app(2, 1, 1), folder(7, 0, 1))
        assertEquals(Cell(2, 1), DropLogic.largeFolderSpot(7, 0, 1, 4, 3, blockers))
        val full = (0 until 4).map { app(it.toLong() + 10, it % 2, it / 2) } + folder(7, 0, 0)
        assertNull(DropLogic.largeFolderSpot(7, 0, 0, 2, 2, full.filter { it.id != 10L }))
    }

    // --- Widgets

    private fun widget(id: Long, x: Int, y: Int, sx: Int, sy: Int, type: Int = ItemType.WIDGET) =
        PlacedItem(id, Container.DESKTOP, 0, x, y, sx, sy, null, type)

    private fun decideWidget(items: List<PlacedItem>, desired: Cell, sx: Int, sy: Int, container: Int = Container.DESKTOP) =
        DropLogic.decide(container, 0, 4, 6, items, desired, desired, true, 99, ItemType.WIDGET, sx, sy)

    @Test fun widgetOverSameSizeWidgetMakesAStack() {
        assertEquals(DropAction.CreateStack(1), decideWidget(listOf(widget(1, 0, 2, 2, 2)), Cell(0, 2), 2, 2))
    }

    @Test fun widgetOverSameSizeStackJoinsIt() {
        assertEquals(DropAction.AddToStack(5), decideWidget(listOf(widget(5, 2, 0, 2, 2, ItemType.STACK)), Cell(2, 0), 2, 2))
    }

    @Test fun widgetOfAnotherSizeIsPlacedBesideInstead() {
        val r = decideWidget(listOf(widget(1, 0, 0, 2, 2)), Cell(0, 0), 4, 1)
        assertEquals(DropAction.Move(Container.DESKTOP, 0, 0, 2), r)
    }

    @Test fun widgetsDoNotGoInTheDock() {
        assertEquals(DropAction.Reject, decideWidget(emptyList(), Cell(0, 0), 1, 1, Container.DOCK))
    }

    @Test fun appsDoNotMakeStacks() {
        val r = DropLogic.decide(Container.DESKTOP, 0, 4, 6, listOf(widget(1, 0, 0, 1, 1)), Cell(0, 0), Cell(0, 0), true, 99, ItemType.APP, 1, 1)
        assertTrue(r is DropAction.Move)
    }

    @Test fun nothingIsDroppedIntoALockedFolder() {
        val r = DropLogic.decide(
            Container.DESKTOP, 0, 4, 6, listOf(folder(7, 0, 0)), Cell(0, 0), Cell(0, 0), true, 99, ItemType.APP, 1, 1,
            locked = setOf(7L),
        )
        assertTrue(r is DropAction.Move)
    }
}
