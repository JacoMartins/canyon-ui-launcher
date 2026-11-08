package io.github.jacomartins.canyonui.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jacomartins.canyonui.R

/*
 * Settings lists as Canyon Orchestrator draws them (its phone app's GroupedList and GroupRow):
 * rows in one rounded panel, divided by hairlines that start after the icon, each with a small
 * coloured icon tile, a title and a line under it, and at the right a value, a switch, or the
 * caret that says a tap goes somewhere. Rows light up while pressed instead of shrinking.
 */

/** The icon tiles' colours: what kind of thing a row is. */
object Tiles {
    val Blue = Color(0xFF3D5AFE)
    val Teal = Color(0xFF1E95A8)
    val Green = Color(0xFF2E9E6B)
    val Orange = Color(0xFFE07B39)
    val Purple = Color(0xFF7C5CE0)
    val Pink = Color(0xFFD9467A)
    val Amber = Color(0xFFD39223)
    val Red = Color(0xFFD9534B)
    val Grey = Color(0xFF4B585B)
}

/** A small rounded square holding an icon, lit a little from above. */
@Composable
fun IconTile(@DrawableRes icon: Int, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** The rows of one [ListGroup]. */
class ListGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** Rows in one rounded panel, divided by hairlines, with an optional caption above and note below. */
@Composable
fun ListGroup(title: String? = null, footer: String? = null, build: ListGroupScope.() -> Unit) {
    val rows = ListGroupScope().apply(build).rows
    if (rows.isEmpty()) return
    // A small margin from the screen's sides, and the interface's border (the depth ring).
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).padding(top = if (title != null) Spacing.md else Spacing.none)) {
        if (title != null) SectionHeader(title, Modifier.padding(start = Spacing.md, bottom = Spacing.sm))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDarkSurface()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().oneDepthBorder(cornerRadius = 20.dp),
        ) {
            Column {
                rows.forEachIndexed { i, row ->
                    if (i > 0) ListDivider()
                    row()
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm),
            )
        }
    }
}

/** The hairline between rows, starting after the icon tile. */
@Composable
fun ListDivider() {
    Box(
        Modifier
            .padding(start = 58.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

/**
 * A row: an icon tile, a title (and a line under it), and at the right a [value], a [trailing]
 * control, and the caret when a tap goes somewhere.
 */
@Composable
fun ListRow(
    title: String,
    @DrawableRes icon: Int? = null,
    tile: Color = Tiles.Grey,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    caret: Boolean = onClick != null,
    role: Role? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val light by animateColorAsState(if (pressed) colors.surfaceContainerHighest else Color.Transparent, tween(80), label = "rowPress")
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = role, onClick = onClick) else Modifier)
            .background(light)
            .heightIn(min = 50.dp)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smMd),
    ) {
        if (icon != null) IconTile(icon, tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (value != null) {
            Text(
                value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        trailing?.invoke()
        if (caret) {
            Icon(
                painterResource(R.drawable.ph_caret_left), contentDescription = null, tint = colors.outline,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

/** A row with a switch; the whole row toggles it. */
@Composable
fun ListSwitchRow(title: String, @DrawableRes icon: Int?, tile: Color, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) =
    ListRow(title, icon, tile, subtitle, onClick = { onChange(!checked) }, caret = false, role = Role.Switch) {
        Switch(checked = checked, onCheckedChange = null)
    }

/** A row that is one choice of several; the whole row picks it. */
@Composable
fun ListRadioRow(title: String, @DrawableRes icon: Int?, tile: Color, selected: Boolean, onSelect: () -> Unit, subtitle: String? = null) =
    ListRow(title, icon, tile, subtitle, onClick = onSelect, caret = false, role = Role.RadioButton) {
        RadioButton(selected = selected, onClick = null)
    }

/** A row holding any content (a row of choices), padded like the others. */
@Composable
fun ListContentRow(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}
