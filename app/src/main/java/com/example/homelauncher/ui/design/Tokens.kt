package com.example.homelauncher.ui.design

import androidx.compose.material3.Typography
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
 * headlines, semi-bold large titles, medium small titles, bold button labels.
 */
val LauncherTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}
