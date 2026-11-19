package io.github.jacomartins.canyonui.ui.design

/**
 * The search bars' own frost, over the frosted or glass effect: a faint white haze. Home's
 * search bar and the Apps screen's search field share it (and their tint and size), so they
 * look the same.
 */
object Frost {
    /** The haze, as ARGB. */
    const val HAZE = 0x14FFFFFF

    /** The tint under the frost over a dark wallpaper, and over a light one. */
    const val TINT_DARK = 0x5E000000
    const val TINT_LIGHT = 0x40FFFFFF
}

/**
 * The search icon's gradient on the search bars (home's, the Apps screen's, search's): light
 * blue to teal in light mode, deeper tones of them in dark mode. ARGB, from the top-left.
 */
object SearchIconGradient {
    // Violet at the lens's top left, through blue, to teal at the handle (diagonal); a little
    // deeper in light mode so it holds against a light field.
    // Bright and saturated (asked: the first ones read too dark).
    private val LIGHT = intArrayOf(0xFF9A66FF.toInt(), 0xFF2F8CFF.toInt(), 0xFF00C9A8.toInt())
    private val DARK = intArrayOf(0xFFB07CFF.toInt(), 0xFF45A6FF.toInt(), 0xFF1AF0CE.toInt())

    fun colors(dark: Boolean): IntArray = if (dark) DARK else LIGHT
}
