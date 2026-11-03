package io.github.jacomartins.canyonui.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorCodeTest {
    private val current = 0xA65B8DEF.toInt()

    @Test fun sixDigitsKeepTheOpacity() {
        assertEquals(0xA6FF8800.toInt(), ColorCode.parse("#ff8800", current))
        assertEquals(0xA6FF8800.toInt(), ColorCode.parse("FF8800", current))
    }

    @Test fun eightDigitsSetIt() {
        assertEquals(0x80FF8800.toInt(), ColorCode.parse("#80FF8800", current))
        assertEquals(0xFF123456.toInt(), ColorCode.parse("#FF123456", current))
    }

    @Test fun shortCodes() {
        assertEquals(0xA6FFAA00.toInt(), ColorCode.parse("#FA0", current))
        assertEquals(0x88FFAA00.toInt(), ColorCode.parse("#8FA0", current))
    }

    @Test fun notCodes() {
        assertNull(ColorCode.parse("", current))
        assertNull(ColorCode.parse("#12345", current))
        assertNull(ColorCode.parse("#GG0000", current))
        assertNull(ColorCode.parse("#123456789", current))
    }

    @Test fun formatAndComplete() {
        assertEquals("#5B8DEF", ColorCode.format(0xFF5B8DEF.toInt()))
        assertEquals("#A65B8DEF", ColorCode.format(current))
        assertTrue(ColorCode.complete("#FF8800"))
        assertFalse(ColorCode.complete("#FF88"))
        for (c in listOf(current, 0xFF000000.toInt(), 0x00FFFFFF)) assertEquals(c, ColorCode.parse(ColorCode.format(c), c))
    }
}
