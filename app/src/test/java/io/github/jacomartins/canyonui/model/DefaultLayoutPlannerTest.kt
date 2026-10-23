package io.github.jacomartins.canyonui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLayoutPlannerTest {

    private fun k(p: String) = AppKey(p, "$p.Main", 0)

    @Test fun dockFirstWithoutDuplicatesOrMissingRoles() {
        val plan = DefaultLayoutPlanner.plan(
            dockCandidates = listOf(k("phone"), null, k("browser"), k("phone"), k("camera")),
            pageCandidates = listOf(k("browser"), k("store"), k("gallery")),
            dockSlots = 4,
            columns = 5,
        )
        assertEquals(listOf(k("phone"), k("browser"), k("camera")), plan.dock)
        assertEquals(listOf(k("store"), k("gallery")), plan.firstPageRow)
    }

    @Test fun dockOverflowCandidatesCanStillGoOnThePage() {
        val plan = DefaultLayoutPlanner.plan(
            dockCandidates = listOf(k("a"), k("b"), k("c")),
            pageCandidates = listOf(k("c"), k("d")),
            dockSlots = 2,
            columns = 5,
        )
        assertEquals(listOf(k("a"), k("b")), plan.dock)
        assertEquals(listOf(k("c"), k("d")), plan.firstPageRow)
    }

    @Test fun pageRowIsLimitedToOneRow() {
        val plan = DefaultLayoutPlanner.plan(emptyList(), (1..9).map { k("p$it") }, 4, 4)
        assertEquals(4, plan.firstPageRow.size)
    }

    @Test fun dockPositionsAreCentred() {
        assertEquals(listOf(0, 1, 2, 3), DefaultLayoutPlanner.dockPositions(4, 4))
        assertEquals(listOf(1, 2, 3), DefaultLayoutPlanner.dockPositions(3, 5))
        assertEquals(listOf(0, 1, 2, 3, 4), DefaultLayoutPlanner.dockPositions(7, 5))
    }
}
