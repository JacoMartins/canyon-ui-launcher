package io.github.jacomartins.canyonui.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/*
 * The depth ring from the OneDepth design system (its "angular border"): a rim lit from the top,
 * a weaker bounce light at the bottom, and dark crescents at the left and right tips. Used on
 * interface surfaces only (buttons, search fields, bars, cards), never on app icons.
 *
 * Differences from the OneDepth original: the ring is plain translucent white (light) and black
 * (tips) drawn straight over the surface, with no blend modes and no offscreen mask layer, so it
 * cannot glitch where layers meet; brushes are built once per size in drawWithCache (no per-frame
 * allocation); rounded rectangles are supported as well as pills.
 */

private const val STEPS = 32

/** Vertical light: [key] hits the top edge, [fill] bounces off the bottom. Scales with height. */
data class DepthLight(
    val key: Float = 0.9f,
    val fill: Float = 0.35f,
    val spread: Float = 0.6f,
    val softness: Float = 0.65f,
) {
    companion object {
        /** Wide, long surfaces such as bars and search fields. */
        val Surface = DepthLight(key = 0.9f, fill = 0.35f, spread = 0.6f)

        /** Short surfaces such as buttons: a tighter glow. */
        val Button = DepthLight(key = 0.8f, fill = 0.25f, spread = 0.5f)
    }
}

// 1 at x = 0, easing to 0 at x >= 1.
private fun glow(x: Float, softness: Float): Float =
    if (x >= 1f) 0f else (0.5f + 0.5f * cos(PI.toFloat() * x)).pow(softness)

private fun DepthLight.brush(height: Float): Brush {
    val s = spread.coerceAtLeast(0.01f)
    return Brush.verticalGradient(
        colorStops = Array(STEPS + 1) { i ->
            val t = i / STEPS.toFloat()
            val a = maxOf(key * glow(t / s, softness), fill * glow((1f - t) / s, softness))
            t to Color.White.copy(alpha = a)
        },
        startY = 0f,
        endY = height,
    )
}

private val TIP_EASE = listOf(0f to 1f, .25f to .84f, .5f to .5f, .75f to .16f, 1f to 0f)

/**
 * Draws the depth ring over the content's bounds.
 * [cornerRadius] null means a pill (radius = half the height).
 */
fun Modifier.depthRing(
    cornerRadius: Dp? = null,
    width: Dp = 1.dp,
    alpha: Float = 0.333f,
    light: DepthLight = DepthLight.Surface,
    sideWidth: Dp = 1.dp,
    sideAlpha: Float = 0.20f,
    sideColor: Color = Color.Black,
): Modifier = this
    .drawWithCache {
        val w = width.toPx()
        val sidePx = sideWidth.toPx()
        val h = size.height
        val r = (cornerRadius?.toPx() ?: (h / 2f)).coerceAtMost(minOf(h, size.width) / 2f)
        // Tip crescents only make sense on shapes wider than tall; on a circle they would pinch
        // the ring out of round, so circles get the lit ring alone.
        val s = if (size.width - h < 1f) 0f else sidePx

        val ringBrush = light.brush(h)
        val ringTopLeft = Offset(s + w / 2f, w / 2f)
        val ringSize = Size((size.width - 2f * s - w).coerceAtLeast(0f), h - w)
        val ringRadius = CornerRadius((r - w / 2f).coerceAtLeast(0f))
        val ringStroke = Stroke(w)

        val sideBrush = if (s > 0.01f && size.width > 0f) {
            val fade = (r / size.width).coerceAtMost(0.5f)
            val stops = TIP_EASE.map { (t, a) -> fade * t to sideColor.copy(alpha = a) } +
                TIP_EASE.reversed().map { (t, a) -> 1f - fade * t to sideColor.copy(alpha = a) }
            Brush.horizontalGradient(colorStops = stops.toTypedArray(), startX = 0f, endX = size.width)
        } else {
            null
        }
        val sideTopLeft = Offset(s / 2f, s / 2f)
        val sideSize = Size(size.width - s, h - s)
        val sideRadius = CornerRadius((r - s / 2f).coerceAtLeast(0f))
        val sideStroke = Stroke(s)

        onDrawWithContent {
            drawContent()
            // 1) Lit ring, inset by the side stroke so a crescent opens at each tip.
            drawRoundRect(ringBrush, ringTopLeft, ringSize, ringRadius, style = ringStroke, alpha = alpha)
            // 2) Dark tips easing to transparent along the straight edges.
            if (sideBrush != null) {
                drawRoundRect(sideBrush, sideTopLeft, sideSize, sideRadius, style = sideStroke, alpha = sideAlpha)
            }
        }
    }

/** True when the current theme's surfaces are dark. */
@Composable
@ReadOnlyComposable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Ring for bars, search fields, panels and cards. Every border in the interface is the button
 * border (so they all match, including the View-drawn ones in [DepthRingPainter]).
 */
@Composable
fun Modifier.surfaceRing(cornerRadius: Dp? = null): Modifier = buttonRing(cornerRadius)

/** Ring tuned for buttons (OneDepth's button values). */
@Composable
fun Modifier.buttonRing(cornerRadius: Dp? = null): Modifier {
    val dark = isDarkSurface()
    return depthRing(
        cornerRadius = cornerRadius,
        alpha = if (dark) 0.40f else 0.60f,
        light = DepthLight.Button,
        sideAlpha = if (dark) 0.40f else 0.25f,
    )
}
