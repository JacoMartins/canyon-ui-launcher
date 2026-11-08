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
    const val LIGHT_START = 0xFF4FC3F7.toInt()
    const val LIGHT_END = 0xFF26A69A.toInt()
    const val DARK_START = 0xFF2F7FBF.toInt()
    const val DARK_END = 0xFF1B8077.toInt()

    fun of(dark: Boolean): Pair<Int, Int> = if (dark) DARK_START to DARK_END else LIGHT_START to LIGHT_END
}
