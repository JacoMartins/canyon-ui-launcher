package io.github.jacomartins.canyonui.model

import io.github.jacomartins.canyonui.model.AppCategories.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class AppCategoriesTest {

    companion object {
        // Unit tests run from the module folder, so the asset is read straight from the source tree.
        private val FILE = File("src/main/assets/${AppCategories.FILE}")

        @BeforeClass @JvmStatic fun loadTable() = AppCategories.load { FILE.readText() }
    }

    private val undefined = -1
    private val productivity = 7

    private fun cat(pkg: String, label: String = "", declared: Int = undefined) = AppCategories.categorize(pkg, label, declared)

    @Test fun knownAppsUseTheTable() {
        assertEquals(Category.COMMUNICATION, cat("com.discord"))
        assertEquals(Category.TRIP, cat("com.ubercab"))
        assertEquals(Category.EDUCATION_AI, cat("com.openai.chatgpt"))
    }

    @Test fun tableWinsOverTheDeclaredCategory() {
        assertEquals(Category.CREATION, cat("com.canva.editor", declared = 2))
    }

    @Test fun familiesUseTheLongestPrefix() {
        assertEquals(Category.GAMES, cat("com.supercell.hayday"))
        assertEquals(Category.SERVICES, cat("br.gov.some.app"))
        // A more specific family (the central bank) beats the government one.
        assertEquals(Category.FINANCE, cat("br.gov.bcb.anything"))
        // An exact entry beats every family.
        assertEquals(Category.WELLNESS, cat("br.gov.datasus.cnsdigital"))
    }

    @Test fun declaredCategoryIsUsed() {
        assertEquals(Category.GAMES, cat("com.example.unknown", declared = 0))
        assertEquals(Category.TRIP, cat("com.example.unknown", declared = 6))
    }

    @Test fun wordsBeatADeclaredProductivity() {
        // Real names from a phone where everything below had landed in Productivity or Others.
        val cases = mapOf(
            "Burger King" to Category.FOOD,
            "Decolar" to Category.TRIP,
            "Drogasil" to Category.WELLNESS,
            "Lojas Renner" to Category.SHOPPING,
            "Ingresso.com" to Category.ENTERTAINMENT,
            "Bicicletar" to Category.TRIP,
            "Zul+" to Category.TRIP,
            "Treino" to Category.WELLNESS,
            "Manga Plus" to Category.NEWS,
            "MyInsta" to Category.COMMUNICATION,
            "Clube Tem Mais pra Você" to Category.SHOPPING,
            "3D Calculator" to Category.TOOLS,
            "Activity Launcher" to Category.TOOLS,
            "AnyDesk" to Category.TOOLS,
            "Remote ADB" to Category.TOOLS,
            "Tailscale" to Category.TOOLS,
            "zFile" to Category.TOOLS,
            "zFont 3" to Category.TOOLS,
            "Corpvs Rastreamento" to Category.TOOLS,
            "Cagece" to Category.SERVICES,
            "Enel Ceará" to Category.SERVICES,
            "Gov.br" to Category.SERVICES,
            "e-Título" to Category.SERVICES,
            "CNH do Brasil" to Category.SERVICES,
            "Minha Claro" to Category.SERVICES,
            "Vivo" to Category.SERVICES,
            "Nio" to Category.SERVICES,
            "Mundo Oi" to Category.SERVICES,
            "Libercard Recarga" to Category.TRIP,
            "Meta AI" to Category.EDUCATION_AI,
            "Sticker Maker" to Category.CREATION,
            "Code Editor" to Category.CREATION,
            "ChatGPT clone" to Category.EDUCATION_AI,
            "Video Editor Pro" to Category.CREATION,
            "Period Tracker" to Category.WELLNESS,
        )
        for ((label, expected) in cases) {
            assertEquals(label, expected, cat("com.example.x", label, productivity))
        }
    }

    @Test fun packageWordsCount() {
        assertEquals(Category.FINANCE, cat("br.com.somebank.app"))
        assertEquals(Category.SHOPPING, cat("com.lojasrenner.x"))
        // Without any word, a declared Productivity is kept.
        assertEquals(Category.PRODUCTIVITY, cat("com.example.x", "Zzyzx", productivity))
    }

    @Test fun unknownAppsStayUncategorized() {
        assertNull(cat("com.example.unknown"))
        // Short fragments must not match by accident.
        assertNull(cat("com.example.business.schedule"))
        assertNull(cat("com.example.x", "iLiquid"))
    }

    @Test fun facebookIsNotABook() {
        assertEquals(Category.COMMUNICATION, cat("com.facebook.something"))
    }

    @Test fun tableHasNoRepeatsOrUnknownSections() {
        val seen = HashMap<String, String>()
        var section = ""
        val repeats = ArrayList<String>()
        for (raw in FILE.readLines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                section = line.trim('[', ']')
                assertTrue("unknown section $section", Category.fromKey(section) != null && section != "others")
                continue
            }
            val previous = seen.put(line, section)
            if (previous != null) repeats += "$line ($previous, $section)"
        }
        assertTrue("repeated lines: $repeats", repeats.isEmpty())
    }
}
