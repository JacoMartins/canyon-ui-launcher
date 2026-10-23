package io.github.jacomartins.canyonui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.jacomartins.canyonui.ui.design.LauncherTypography

/**
 * Canyon UI's own palette: neutral greys only (no hue), one set for light mode and one for dark.
 * Dark mode is built on #010101 (background) and #171717 (panels). Used unless the user turns on
 * "Use the phone's colour palette" (whether the phone's palette is switched on cannot be told
 * apart from its default through public APIs).
 */
private fun grey(v: Int) = Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v)

private val CanyonLight = lightColorScheme(
    primary = grey(0x17),
    onPrimary = grey(0xFF),
    primaryContainer = grey(0xE0),
    onPrimaryContainer = grey(0x17),
    inversePrimary = grey(0xF2),
    secondary = grey(0x40),
    onSecondary = grey(0xFF),
    secondaryContainer = grey(0xE8),
    onSecondaryContainer = grey(0x17),
    tertiary = grey(0x52),
    onTertiary = grey(0xFF),
    tertiaryContainer = grey(0xE0),
    onTertiaryContainer = grey(0x17),
    background = grey(0xFA),
    onBackground = grey(0x17),
    surface = grey(0xFA),
    onSurface = grey(0x17),
    surfaceVariant = grey(0xE8),
    onSurfaceVariant = grey(0x5C),
    surfaceTint = grey(0x17),
    inverseSurface = grey(0x26),
    inverseOnSurface = grey(0xF5),
    outline = grey(0xA3),
    outlineVariant = grey(0xD4),
    surfaceBright = grey(0xFF),
    surfaceDim = grey(0xE0),
    surfaceContainerLowest = grey(0xFF),
    surfaceContainerLow = grey(0xF5),
    surfaceContainer = grey(0xEF),
    surfaceContainerHigh = grey(0xE8),
    surfaceContainerHighest = grey(0xE0),
    scrim = Color.Black,
)

private val CanyonDark = darkColorScheme(
    primary = grey(0xF2),
    onPrimary = grey(0x0A),
    primaryContainer = grey(0x2E),
    onPrimaryContainer = grey(0xF2),
    inversePrimary = grey(0x17),
    secondary = grey(0xD4),
    onSecondary = grey(0x0A),
    secondaryContainer = grey(0x26),
    onSecondaryContainer = grey(0xF2),
    tertiary = grey(0xBD),
    onTertiary = grey(0x0A),
    tertiaryContainer = grey(0x2E),
    onTertiaryContainer = grey(0xF2),
    background = grey(0x01),
    onBackground = grey(0xF2),
    surface = grey(0x01),
    onSurface = grey(0xF2),
    surfaceVariant = grey(0x26),
    onSurfaceVariant = grey(0xA3),
    surfaceTint = grey(0xF2),
    inverseSurface = grey(0xE5),
    inverseOnSurface = grey(0x17),
    outline = grey(0x5C),
    outlineVariant = grey(0x2E),
    surfaceBright = grey(0x2E),
    surfaceDim = grey(0x01),
    surfaceContainerLowest = grey(0x00),
    surfaceContainerLow = grey(0x0C),
    surfaceContainer = grey(0x17),
    surfaceContainerHigh = grey(0x1F),
    surfaceContainerHighest = grey(0x26),
    scrim = Color.Black,
)

/** [phonePalette]: follow the phone's wallpaper-based palette (Android 12+) instead of Canyon's. */
@Composable
fun HomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    phonePalette: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        phonePalette && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> CanyonDark
        else -> CanyonLight
    }
    MaterialTheme(colorScheme = colors, typography = LauncherTypography, content = content)
}
