package com.example.homelauncher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFilterTest {

    @Test fun parsesFullComponent() {
        assertEquals(
            "com.whatsapp" to "com.whatsapp.Main",
            AppFilter.parseComponent("ComponentInfo{com.whatsapp/com.whatsapp.Main}"),
        )
    }

    @Test fun expandsRelativeClass() {
        assertEquals("com.a" to "com.a.ui.Home", AppFilter.parseComponent(" ComponentInfo{com.a/.ui.Home} "))
    }

    @Test fun rejectsMalformedAndPlaceholders() {
        assertNull(AppFilter.parseComponent(null))
        assertNull(AppFilter.parseComponent(""))
        assertNull(AppFilter.parseComponent("ComponentInfo{}"))
        assertNull(AppFilter.parseComponent("ComponentInfo{com.a}"))
        assertNull(AppFilter.parseComponent("ComponentInfo{com.a/}"))
        assertNull(AppFilter.parseComponent(":LAUNCHER_ACTION_APP_DRAWER"))
        assertNull(AppFilter.parseComponent("ComponentInfo{com.a/com.a.Main"))
    }

    @Test fun calendarName() {
        assertEquals("calendar_7", AppFilter.calendarDrawable("calendar_", 7))
    }

    @Test fun lookupPrefersExactActivityThenPackage() {
        val byComponent = mapOf(("com.a" to "com.a.Main") to "exact")
        val byPackage = mapOf("com.a" to "pkg", "com.b" to "other")
        assertEquals("exact", AppFilter.lookup("com.a", "com.a.Main", byComponent, byPackage))
        assertEquals("pkg", AppFilter.lookup("com.a", "com.a.Renamed", byComponent, byPackage))
        assertNull(AppFilter.lookup("com.c", "com.c.Main", byComponent, byPackage))
    }

    @Test fun weekdayVariantName() {
        assertEquals("calendar_31_fri", AppFilter.calendarWeekdayDrawable("calendar_", 31, java.util.Calendar.FRIDAY))
        assertEquals("calendar_1_sun", AppFilter.calendarWeekdayDrawable("calendar_", 1, java.util.Calendar.SUNDAY))
        assertEquals("calendar_7_sat", AppFilter.calendarWeekdayDrawable("calendar_", 7, java.util.Calendar.SATURDAY))
    }
}
