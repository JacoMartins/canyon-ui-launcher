package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * The search bars' one look (home's bar, the Apps screen's and search's field, the widget
 * picker's search): a black field, the bold search icon,
 * "Search"-style text.
 */

/** The search bars' fill: black, in both themes (home's bar takes it a little see-through). */
@Composable
fun searchBarFill(): Color = backdropColor(Color.Black.copy(alpha = 0.92f))

/** The placeholder, the mic and clear icons on the black bars. */
val SearchBarContent = Color(0xFFBDBDC2)

/** Typed text on the black bars. */
val SearchBarInk = Color(0xFFF2F2F4)

/** Home's bar keeps this much of the field's opacity. */
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
