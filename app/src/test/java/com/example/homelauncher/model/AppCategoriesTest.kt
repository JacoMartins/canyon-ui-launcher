package com.example.homelauncher.model

import com.example.homelauncher.model.AppCategories.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCategoriesTest {

    private val undefined = -1

    @Test fun knownAppsUseTheTable() {
        assertEquals(Category.COMMUNICATION, AppCategories.categorize("com.discord", undefined))
        assertEquals(Category.TRIP, AppCategories.categorize("com.ubercab", undefined))
        assertEquals(Category.EDUCATION_AI, AppCategories.categorize("com.openai.chatgpt", undefined))
    }

    @Test fun tableWinsOverTheDeclaredCategory() {
        // Whatever an app declares, the table decides for the apps it knows.
        assertEquals(Category.CREATION, AppCategories.categorize("com.canva.editor", 2))
    }

    @Test fun declaredCategoryIsUsed() {
        assertEquals(Category.GAMES, AppCategories.categorize("com.example.unknown", 0))
        assertEquals(Category.TRIP, AppCategories.categorize("com.example.unknown", 6))
    }

    @Test fun packageWordsAreTheLastResort() {
        assertEquals(Category.FINANCE, AppCategories.categorize("br.com.somebank.app", undefined))
    }

    @Test fun unknownAppsStayUncategorized() {
        assertNull(AppCategories.categorize("com.example.unknown", undefined))
        // Short fragments must not match by accident.
        assertNull(AppCategories.categorize("com.example.business.schedule", undefined))
    }
}
