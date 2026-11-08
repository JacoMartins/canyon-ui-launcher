package io.github.jacomartins.canyonui.ui.design

/**
 * Colour codes as people type them: #RGB, #RRGGBB (these keep the current opacity), #ARGB and
 * #AARRGGBB (with their own), the # optional. Pure (tested).
 */
object ColorCode {

    /** [text] as ARGB, the opacity from [current] when the code has none; null if not a code. */
    fun parse(text: String, current: Int): Int? {
        val h = text.trim().removePrefix("#")
        if (h.isEmpty() || h.any { it.digitToIntOrNull(16) == null }) return null
        val full = when (h.length) {
            3, 4 -> h.map { "$it$it" }.joinToString("")
            6, 8 -> h
            else -> return null
        }
        val v = full.toLong(16).toInt()
        return if (full.length == 6) (current and 0xFF000000.toInt()) or v else v
    }

    /** Whether [text] is a full code (6 or 8 digits), as opposed to a short one on the way there. */
    fun complete(text: String): Boolean = text.trim().removePrefix("#").length.let { it == 6 || it == 8 }

    /** #AARRGGBB, or #RRGGBB for an opaque colour. */
    fun format(argb: Int): String =
        if (argb ushr 24 == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
}
