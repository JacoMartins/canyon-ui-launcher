package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * page headlines (h1), semi-bold section headlines and titles (h2, h3), bold button labels.
 */
val LauncherTypography: Typography = Typography(
    // The phone's own font. Compose's default family resolves the stock sans-serif and skips a
    // font the user picked in the phone's settings; the platform default typeface (what the
    // home screen's View labels use) carries it.
).let { base ->
    val phone = FontFamily(android.graphics.Typeface.DEFAULT)
    Typography(
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
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}
