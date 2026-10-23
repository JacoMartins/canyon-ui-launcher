package io.github.jacomartins.canyonui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderTest {

    private fun arrange(
        current: List<String>,
        scores: Map<String, Int> = emptyMap(),
        upSince: MutableMap<String, Long> = HashMap(),
        known: Set<String> = current.toSet(),
        today: Long = 100,
    ) = CategoryOrder.arrange(current, { it }, { scores[it] ?: 0 }, upSince, known, today)

    @Test fun newFolderStartsAlphabetical() {
        assertEquals(listOf("a", "b", "c"), arrange(listOf("c", "a", "b"), known = emptySet()))
    }

    @Test fun moreUsedAppWaitsFiveDaysThenSwaps() {
        val up = HashMap<String, Long>()
        val order = listOf("a", "b", "c")
        val scores = mapOf("c" to 9)
        // Deserves first place from day 100: nothing moves for five days.
        assertEquals(order, arrange(order, scores, up, today = 100))
        assertEquals(order, arrange(order, scores, up, today = 104))
        // Day 105: it swaps with the app in first place.
        assertEquals(listOf("c", "b", "a"), arrange(order, scores, up, today = 105))
    }

    @Test fun waitingStopsWhenTheAppNoLongerDeservesToMove() {
        val up = HashMap<String, Long>()
        val order = listOf("a", "b")
        arrange(order, mapOf("b" to 3), up, today = 100)
        arrange(order, emptyMap(), up, today = 102)
        // The count starts again from day 103.
        assertEquals(order, arrange(order, mapOf("b" to 3), up, today = 106))
    }

    @Test fun newAppTakesItsPlaceAtOnce() {
        val r = arrange(listOf("a", "c", "b"), known = setOf("a", "c"))
        // a and c keep their order; b goes where it belongs among them.
        assertEquals(listOf("a", "b", "c"), r)
    }
}
