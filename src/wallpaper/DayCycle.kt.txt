package io.github.jacomartins.canyonui.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader

/**
 * The Day cycle wallpaper's light: the user's photo dimmed and cooled at night, warm at dawn and
 * dusk, plain at noon, with a soft tint from the top of the sky. [at] is pure (tested); [draw]
 * paints a photo with it.
 */
object DayCycle {

    /**
     * [brightness] scales the photo; [warmth] (-1 cool .. 1 warm) shifts red against blue;
     * [saturation] as in a colour matrix; [tint] (ARGB) lies over the top of the picture, fading
     * to a third of it at the bottom.
     */
    data class Light(val brightness: Float, val warmth: Float, val saturation: Float, val tint: Int)

    // Minute of the day -> light, in order; the day wraps from the last back to the first.
    private val KEYS = listOf(
        0 to Light(0.55f, -0.6f, 0.80f, 0x40102050),
        300 to Light(0.60f, -0.4f, 0.85f, 0x30203060),
        390 to Light(0.85f, 0.6f, 1.00f, 0x30FF8A60),
        480 to Light(0.98f, 0.25f, 1.05f, 0x14FFC080),
        720 to Light(1.00f, 0.0f, 1.05f, 0x00FFFFFF),
        990 to Light(0.98f, 0.35f, 1.08f, 0x18FFB060),
        1110 to Light(0.80f, 0.7f, 1.10f, 0x40FF6A50),
        1200 to Light(0.60f, -0.3f, 0.90f, 0x40304080),
    )

    private const val DAY = 24 * 60

    /** The light at [minuteOfDay] (0..1439), blended between the two nearest key times. */
    fun at(minuteOfDay: Int): Light {
        val m = ((minuteOfDay % DAY) + DAY) % DAY
        val i = KEYS.indexOfLast { it.first <= m }
        val (m0, a) = KEYS[i]
        val (m1raw, b) = KEYS[(i + 1) % KEYS.size]
        val m1 = if (m1raw <= m0) m1raw + DAY else m1raw
        val k = (m - m0).toFloat() / (m1 - m0)
        return Light(
            lerp(a.brightness, b.brightness, k),
            lerp(a.warmth, b.warmth, k),
            lerp(a.saturation, b.saturation, k),
            lerpColor(a.tint, b.tint, k),
        )
    }

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

    private fun lerpColor(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = ((((a ushr shift) and 0xFF) + (((b ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) * k) + 0.5f).toInt() and 0xFF
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // Made when first drawing ([at] stays free of Android classes, for tests).
    private val photoPaint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val tintPaint by lazy { Paint() }

    /** [photo] filling [dst] on [canvas], lit by [light]. */
    fun draw(canvas: Canvas, photo: Bitmap, dst: Rect, light: Light) {
        val m = ColorMatrix().apply { setSaturation(light.saturation) }
        val b = light.brightness
        val w = light.warmth * 0.08f
        m.postConcat(ColorMatrix().apply { setScale(b * (1f + w), b, b * (1f - w), 1f) })
        photoPaint.colorFilter = ColorMatrixColorFilter(m)
        canvas.drawBitmap(photo, null, dst, photoPaint)
        val top = light.tint
        val bottom = (((top ushr 24) / 3) shl 24) or (top and 0xFFFFFF)
        tintPaint.shader = LinearGradient(0f, dst.top.toFloat(), 0f, dst.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(dst, tintPaint)
    }
}
