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
    private val LIGHT = intArrayOf(0xFF8A6CF2.toInt(), 0xFF3F7FE6.toInt(), 0xFF14B3A0.toInt())
    private val DARK = intArrayOf(0xFFA48CFB.toInt(), 0xFF5C93F2.toInt(), 0xFF2BD3BC.toInt())

    fun colors(dark: Boolean): IntArray = if (dark) DARK else LIGHT
}
