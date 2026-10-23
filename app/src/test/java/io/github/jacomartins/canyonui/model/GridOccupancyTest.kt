package io.github.jacomartins.canyonui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridOccupancyTest {

    @Test fun emptyGridFirstFreeIsTopLeft() {
        assertEquals(Cell(0, 0), GridOccupancy(5, 6).findFirstFree())
    }

    @Test fun markedCellsAreSkippedRowByRow() {
        val g = GridOccupancy(3, 2)
        g.mark(0, 0); g.mark(1, 0); g.mark(2, 0)
        assertEquals(Cell(0, 1), g.findFirstFree())
    }

    @Test fun spansCollide() {
        val g = GridOccupancy(5, 6)
        assertTrue(g.mark(1, 1, 2, 2))
        assertFalse(g.isFree(2, 2))
        assertFalse(g.isFree(0, 0, 2, 2))
        assertTrue(g.isFree(3, 1))
        assertTrue(g.isFree(0, 3, 5, 3))
    }

    @Test fun outOfBoundsNeverFitsOrMarks() {
        val g = GridOccupancy(4, 5)
        assertFalse(g.isFree(4, 0))
        assertFalse(g.isFree(3, 0, 2, 1))
        assertFalse(g.isFree(-1, 0))
        assertFalse(g.mark(0, 4, 1, 2))
        assertEquals(20, g.freeCellCount())
    }

    @Test fun findFirstFreeForSpanNeedsWholeArea() {
        val g = GridOccupancy(4, 2)
        g.mark(1, 0); g.mark(2, 1)
        // 2x2 cannot fit anywhere: column pairs (0,1) and (1,2) and (2,3) are all blocked.
        assertNull(g.findFirstFree(2, 2))
        assertEquals(Cell(2, 0), g.findFirstFree(2, 1))
    }

    @Test fun clearFreesCells() {
        val g = GridOccupancy(2, 2)
        g.mark(0, 0, 2, 2)
        assertNull(g.findFirstFree())
        g.clear(1, 1)
        assertEquals(Cell(1, 1), g.findFirstFree())
    }

    @Test fun fullGridReturnsNull() {
        val g = GridOccupancy(2, 1)
        g.mark(0, 0, 2, 1)
        assertNull(g.findFirstFree())
        assertEquals(0, g.freeCellCount())
    }
}
