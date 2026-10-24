package io.github.jacomartins.canyonui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private val labels = listOf("Câmera", "Calendar", "Google Maps", "Maps Go", "Gmail", "2048", "Settings", "my Files")

    @Test fun normalizeStripsAccentsAndCase() {
        assertEquals("camera", AppSearch.normalize("  Câmera "))
    }

    @Test fun sections() {
        assertEquals("C", AppSearch.sectionOf("câmera"))
        assertEquals("#", AppSearch.sectionOf("2048"))
        assertEquals("#", AppSearch.sectionOf(""))
        assertEquals("É".let { AppSearch.sectionOf(it) }, "E")
    }

    @Test fun sortPutsSymbolsFirstAndIgnoresAccents() {
        val sorted = AppSearch.sortByLabel(labels) { it }
        assertEquals(listOf("2048", "Calendar", "Câmera", "Gmail", "Google Maps", "Maps Go", "my Files", "Settings"), sorted)
    }

    @Test fun filterRanksPrefixThenWordStartThenContains() {
        val sorted = AppSearch.sortByLabel(labels) { it }
        assertEquals(listOf("Maps Go", "Google Maps"), AppSearch.filter(sorted, "maps") { it })
        assertEquals(listOf("Calendar", "Câmera"), AppSearch.filter(sorted, "ca") { it })
        assertEquals(listOf("Gmail"), AppSearch.filter(sorted, "ail") { it })
        assertEquals(listOf("Câmera"), AppSearch.filter(sorted, "CAMERA") { it })
    }

    @Test fun emptyQueryReturnsAll() {
        assertEquals(labels, AppSearch.filter(labels, "  ") { it })
    }

    @Test fun sectionIndexPointsAtFirstItem() {
        val sorted = AppSearch.sortByLabel(labels) { it }
        assertEquals(
            listOf("#" to 0, "C" to 1, "G" to 3, "M" to 5, "S" to 7),
            AppSearch.sectionIndex(sorted),
        )
    }

    @Test fun rankMatchesFilterOrder() {
        org.junit.Assert.assertEquals(0, AppSearch.rank("Câmera", "cam"))
        org.junit.Assert.assertEquals(1, AppSearch.rank("Google Maps", "maps"))
        org.junit.Assert.assertEquals(2, AppSearch.rank("Instagram", "gram"))
        org.junit.Assert.assertEquals(-1, AppSearch.rank("Phone", "x"))
        org.junit.Assert.assertEquals(-1, AppSearch.rank("Phone", ""))
    }
}
