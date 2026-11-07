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
