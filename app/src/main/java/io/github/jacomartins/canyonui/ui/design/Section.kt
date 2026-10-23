package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * The title above a group of settings or choices: 14sp bold in the text colour (white in dark
 * mode), sitting on the page above the group's container rather than inside it.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
