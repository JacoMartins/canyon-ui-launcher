package io.github.jacomartins.canyonui.ui.drawer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jacomartins.canyonui.data.AppInfo
import io.github.jacomartins.canyonui.ui.home.DragLayer
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** A letter of the Apps screen and the index of its first app. */
private class Section(val letter: String, val index: Int)

/** The first letter of [label] without accents, upper case; "#" for digits and symbols. */
private fun letterOf(label: String): String {
    val c = Normalizer.normalize(label.trim().take(1), Normalizer.Form.NFD).firstOrNull() ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

/**
 * The fast scroller at the side of the Apps screen: a column of dots (one per first letter, not
 * the whole alphabet). Touching or sliding along it jumps to that letter and shows the letter in
 * a bubble beside the finger.
 */
@Composable
fun AlphabetScrubber(apps: List<AppInfo>, grid: LazyGridState, touch: DragLayer.DrawerTouchState, modifier: Modifier = Modifier) {
    val sections = remember(apps) {
        val out = ArrayList<Section>()
        apps.forEachIndexed { i, app ->
            val l = letterOf(app.label)
            if (out.lastOrNull()?.letter != l) out += Section(l, i)
        }
        out
    }
    if (sections.size < 2) return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dot = 6.dp
    Box(modifier.fillMaxHeight().width(36.dp)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = sections.joinToString(" ") { it.letter } }
                .pointerInput(sections) {
                    fun pick(y: Float) {
                        fingerY = y
                        val i = (y / size.height * sections.size).toInt().coerceIn(0, sections.size - 1)
                        if (i != active) {
                            active = i
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { grid.scrollToItem(sections[i].index) }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // The drawer must not take this slide as a close gesture.
                        touch.blockDrag = true
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            c.consume()
                            pick(c.position.y)
                        }
                        touch.blockDrag = false
                        active = -1
                    }
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEachIndexed { i, _ ->
                val on = i == active
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(if (on) dot * 1.6f else dot)
                        .drawBehind {
                            drawCircle(colors.onSurface.copy(alpha = if (on) 1f else 0.35f), radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f))
                        },
                )
            }
        }
        if (active >= 0) {
            // The letter beside the finger, outside the dots.
            val bubble = 56.dp
            // From the column's top edge (finger coordinates) to the box's centre line.
            val topPx = with(density) { size(sections.size, dot) / 2f - 8.dp.toPx() }
            Surface(
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(with(density) { -(bubble + 8.dp).roundToPx() }, (fingerY - topPx).roundToInt()) }
                    .size(bubble),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(sections[active].letter, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Height in px of the dot column (8dp padding each end, 6dp dots, 5dp gaps). */
private fun androidx.compose.ui.unit.Density.size(count: Int, dot: androidx.compose.ui.unit.Dp): Float =
    (16.dp + dot * count + 5.dp * (count - 1)).toPx()
