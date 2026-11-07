package io.github.jacomartins.canyonui.ui.design

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

/**
 * The search bars' own frost, over the frosted or glass effect: a faint white haze and a fine
 * grain of soft specks. Home's search bar and the Apps screen's search field share it, so they
 * look the same.
 */
object Frost {
    /** The haze, as ARGB. */
    const val HAZE = 0x14FFFFFF

    /** The tint under the frost over a dark wallpaper, and over a light one. */
    const val TINT_DARK = 0x5E000000
    const val TINT_LIGHT = 0x40FFFFFF

    /** A small tile of soft white specks, to repeat. */
    val grain: Bitmap by lazy {
        val n = 64
        val bmp = createBitmap(n, n)
        val px = IntArray(n * n)
        val random = java.util.Random(7)
        for (i in px.indices) px[i] = (random.nextInt(14) shl 24) or 0xFFFFFF
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        bmp
    }
}
