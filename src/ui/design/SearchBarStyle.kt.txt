package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * The search bars' one look (home's bar, the Apps screen's and search's field, the widget
 * picker's search): a black field (a light one in light mode), the bold search icon,
 * "Search"-style text.
 */

/** How much darker than the theme's highest container the bar is in light mode. */
private const val DARKEN_LIGHT = 0.12f

/**
 * The "Search bar opacity" setting: how much of the bar's colour covers what is behind it, as
 * the final opacity (the frosted or glass effect does not thin it further). Set from settings.
 */
object SearchBarLook {
    const val DEFAULT_OPACITY = 0.35f
    val opacity = kotlinx.coroutines.flow.MutableStateFlow(DEFAULT_OPACITY)
}

/**
 * The search bars' fill: black in dark mode (see-through, like folders, with no frost of its
 * own), a light field in light mode; both at the "Search bar opacity" setting.
 */
@Composable
fun searchBarFill(): Color {
    val opacity = SearchBarLook.opacity.collectAsState().value
    return if (isDarkSurface()) Color.Black.copy(alpha = opacity)
    else lerp(MaterialTheme.colorScheme.surfaceContainerHighest, Color.Black, DARKEN_LIGHT).copy(alpha = opacity)
}

/** The placeholder, the mic and clear icons on the bars. */
@Composable
fun searchBarContent(): Color = if (isDarkSurface()) Color(0xFFBDBDC2) else MaterialTheme.colorScheme.onSurfaceVariant

/** Typed text on the bars. */
@Composable
fun searchBarInk(): Color = if (isDarkSurface()) Color(0xFFF2F2F4) else MaterialTheme.colorScheme.onSurface

/** The widget picker's field keeps this much of the bars' opacity. */
const val HOME_BAR_OPACITY = 0.875f

/** Home's bar's height. */
val SearchBarHeight = 49.5.dp

/** The text in the bars: 16sp medium, centred by the font itself, no extra letter spacing. */
@Composable
fun searchBarText(): TextStyle = MaterialTheme.typography.titleMedium.copy(
    lineHeight = TextUnit.Unspecified,
    letterSpacing = TextUnit.Unspecified,
    lineHeightStyle = null,
)
