package io.github.jacomartins.canyonui.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenesTest {

    @Test fun dayCycleKeysAndBlends() {
        assertEquals(1.0f, DayCycle.at(720).brightness, 1e-6f)
        assertEquals(0.55f, DayCycle.at(0).brightness, 1e-6f)
        // Half way between 12:00 (1.00) and 16:30 (0.98).
        assertEquals(0.99f, DayCycle.at(855).brightness, 1e-4f)
        // Past 20:00 the day wraps towards midnight's light.
        val late = DayCycle.at(1320).brightness
        assertTrue(late in 0.55f..0.60f)
        // Minutes out of range wrap around.
        assertEquals(DayCycle.at(60), DayCycle.at(60 + 1440))
        assertEquals(DayCycle.at(60), DayCycle.at(60 - 1440))
    }

    @Test fun nightIsDarkerAndCoolerThanNoon() {
        val night = DayCycle.at(120)
        val noon = DayCycle.at(720)
        assertTrue(night.brightness < noon.brightness)
        assertTrue(night.warmth < 0f)
        assertTrue(DayCycle.at(1110).warmth > 0.5f)
    }

    @Test fun scenesAreOpaqueAndMove() {
        for (s in listOf(Scene.GRADIENT, Scene.AURORA, Scene.WAVES)) {
            // Whole frames: some scenes (waves) only move at their edges.
            val a = IntArray(16 * 64)
            val b = IntArray(16 * 64)
            SceneMath.render(s, 0f, 16, 64, 2f, a)
            SceneMath.render(s, 40f, 16, 64, 2f, b)
            assertTrue(a.all { it ushr 24 == 0xFF })
            assertNotEquals("$s should move over time", a.toList(), b.toList())
        }
    }

    @Test fun renderFillsEveryPixel() {
        val out = IntArray(8 * 16)
        SceneMath.render(Scene.WAVES, 3f, 8, 16, 2f, out)
        assertTrue(out.all { it ushr 24 == 0xFF })
    }

    @Test fun sceneKeysRoundTrip() {
        for (s in Scene.entries) assertEquals(s, Scene.of(s.key))
        assertEquals(null, Scene.of("nope"))
    }
}
