package io.github.jacomartins.canyonui.ui.design

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A screen's opening: its parts ([entrance], and every [SettingsSection]) rise a little into
 * place and fade in, one just after the other from the top. Only while the screen is opening:
 * parts that appear later (a switch showing more options) are simply there.
 */
class Entrance internal constructor() {
    private val start = SystemClock.uptimeMillis()
    private var count = 0

    /** The delay for the next part, or null once the opening is over. */
    internal fun next(): Long? {
        if (SystemClock.uptimeMillis() - start > WINDOW_MS) return null
        return (count++ * STEP_MS).coerceAtMost(MAX_DELAY_MS)
    }

    private companion object {
        const val WINDOW_MS = 600L
        const val STEP_MS = 40L
        const val MAX_DELAY_MS = 280L
    }
}

private val LocalEntrance = compositionLocalOf<Entrance?> { null }

/** Opens [content] as a screen; give it a new [key] for each screen shown in the same place. */
@Composable
fun EntranceScreen(key: Any? = Unit, content: @Composable () -> Unit) {
    val entrance = remember(key) { Entrance() }
    CompositionLocalProvider(LocalEntrance provides entrance, content = content)
}

private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** This part rises and fades in with its screen's opening (see [Entrance]). */
@Composable
fun Modifier.entrance(): Modifier {
    val delay = LocalEntrance.current.let { e -> remember(e) { e?.next() } } ?: return this
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(DURATION_MS, delayMillis = delay.toInt(), easing = Ease)) }
    val rise = with(LocalDensity.current) { RISE.toPx() }
    return this.graphicsLayer {
        val t = k.value
        alpha = t
        translationY = (1f - t) * rise
        val s = 0.97f + 0.03f * t
        scaleX = s
        scaleY = s
    }
}

private const val DURATION_MS = 360
private val RISE = 20.dp
