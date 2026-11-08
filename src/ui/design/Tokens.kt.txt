package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp

/** Spacing scale from the OneDepth design system: base 16dp with named steps. */
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val xsSm = 6.dp
    val sm = 8.dp
    val smMd = 12.dp
    val md = 16.dp
    val mdLg = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
}

/**
 * OneDepth's text hierarchy on the phone's own font (no bundled typeface): bold display and
 * page headlines (h1), semi-bold section headlines (h2), medium item titles (h3), regular body
 * text and bold button labels.
 *
 * Compose ignores the font weight of a family made from a single Typeface, so every weight gets
 * its own family made from the phone's font at that weight (Typeface.create picks the font's own
 * bold face, or emboldens it when there is none).
 */
val LauncherTypography: Typography = Typography(
    // The phone's own font. Compose's default family resolves the stock sans-serif and skips a
    // font the user picked in the phone's settings; the platform default typeface (what the
    // home screen's View labels use) carries it.
).let { base ->
    val phone = FontFamily(android.graphics.Typeface.DEFAULT)
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = phone),
        displayMedium = base.displayMedium.copy(fontFamily = phone),
        displaySmall = base.displaySmall.copy(fontFamily = phone),
        headlineLarge = base.headlineLarge.copy(fontFamily = phone),
        headlineMedium = base.headlineMedium.copy(fontFamily = phone),
        headlineSmall = base.headlineSmall.copy(fontFamily = phone),
        titleLarge = base.titleLarge.copy(fontFamily = phone),
        titleMedium = base.titleMedium.copy(fontFamily = phone),
        titleSmall = base.titleSmall.copy(fontFamily = phone),
        bodyLarge = base.bodyLarge.copy(fontFamily = phone),
        bodyMedium = base.bodyMedium.copy(fontFamily = phone),
        bodySmall = base.bodySmall.copy(fontFamily = phone),
        labelLarge = base.labelLarge.copy(fontFamily = phone),
        labelMedium = base.labelMedium.copy(fontFamily = phone),
        labelSmall = base.labelSmall.copy(fontFamily = phone),
    )
}.let { t ->
    fun TextStyle.weight(w: FontWeight) = copy(
        fontWeight = w,
        fontFamily = FontFamily(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, w.weight, false)),
    )
    t.copy(
        displayLarge = t.displayLarge.weight(FontWeight.Bold),
        displayMedium = t.displayMedium.weight(FontWeight.Bold),
        displaySmall = t.displaySmall.weight(FontWeight.Bold),
        headlineLarge = t.headlineLarge.weight(FontWeight.Bold),
        headlineMedium = t.headlineMedium.weight(FontWeight.Bold),
        headlineSmall = t.headlineSmall.weight(FontWeight.Bold),
        titleLarge = t.titleLarge.weight(FontWeight.SemiBold),
        titleMedium = t.titleMedium.weight(FontWeight.Medium),
        titleSmall = t.titleSmall.weight(FontWeight.Medium),
        // Canyon Orchestrator's scale (OneDepth Kotlin's): titles bold, card and sheet titles
        // semibold, labels and buttons semibold, reading text regular.
        labelLarge = t.labelLarge.weight(FontWeight.SemiBold),
        labelMedium = t.labelMedium.weight(FontWeight.Medium),
        labelSmall = t.labelSmall.weight(FontWeight.Medium),
    )
}.let { t ->
    // Where the text sits in its line: the stock place (by the font's room above and below) sat a
    // little low in buttons and fields with the phone's font, and the exact middle a little high;
    // a share of the line's extra space above it between the two centres it.
    val centred = LineHeightStyle(alignment = LineHeightStyle.Alignment(TEXT_TOP_SHARE), trim = LineHeightStyle.Trim.None)
    fun TextStyle.c() = copy(lineHeightStyle = centred)
    t.copy(
        displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
        headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
        titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
        bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
        labelLarge = t.labelLarge.c(), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
    )
}

/** Text on buttons (Canyon Orchestrator's): body large, semibold. */
val ButtonText: TextStyle
    @androidx.compose.runtime.Composable
    get() = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 600, false)),
    )

/** The share of a line's extra height above the text (0.5 = the middle; see LauncherTypography). */
private const val TEXT_TOP_SHARE = 0.6f
