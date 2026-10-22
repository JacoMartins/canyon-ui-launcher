package com.example.homelauncher.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Filled pill in the primary colour with the depth ring and a bold label. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) = PillButton(text, onClick, modifier, icon, enabled, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

/** Tonal pill for secondary actions: same shape, ring and label weight, quieter colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) = PillButton(
    text, onClick, modifier, icon, enabled,
    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
)

/** A pill that is Primary when [selected] and Secondary otherwise (segmented choices). */
@Composable
fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val m = modifier.semantics { this.selected = selected }
    if (selected) PrimaryButton(text, onClick, m) else SecondaryButton(text, onClick, m)
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    enabled: Boolean,
    container: Color,
    content: Color,
) {
    // Material grows clickable surfaces to a 48dp touch target, which made the ring trace a larger
    // box than the visible pill (a doubled outline). Turn that off and give the pill a 44dp
    // minimum height instead, so the ring, the fill and the touch area are the same shape.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) container else container.copy(alpha = 0.38f),
        contentColor = if (enabled) content else content.copy(alpha = 0.6f),
        modifier = modifier.heightIn(min = 44.dp).buttonRing(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.mdLg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xsSm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
    }
}
