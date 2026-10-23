package io.github.jacomartins.canyonui.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.jacomartins.canyonui.data.RenderMode

/**
 * Frosted and glass modes: draws the blurred wallpaper behind this element, inside [shape],
 * lined up with the real wallpaper. Glass adds a faint sheen at the top. Does nothing in the
 * translucent mode. Put it before the element's own (see-through) background.
 */
fun Modifier.backdrop(shape: Shape): Modifier = composed {
    val blur by WallpaperBackdrop.active.collectAsState()
    val b = blur ?: return@composed this
    val view = LocalView.current
    val image = remember(b) { b.bitmap.asImageBitmap() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val path = remember { Path() }
    this
        .onGloballyPositioned { c ->
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            origin = c.positionInRoot() + Offset(loc[0].toFloat(), loc[1].toFloat())
        }
        .drawBehind {
            path.reset()
            path.addOutline(shape.createOutline(size, layoutDirection, this))
            clipPath(path) {
                drawImage(
                    image,
                    dstOffset = IntOffset(-origin.x.toInt(), -origin.y.toInt()),
                    dstSize = IntSize(b.screenW, b.screenH),
                    filterQuality = FilterQuality.Low,
                )
                if (b.mode == RenderMode.GLASS) {
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.5f to Color.Transparent))
                }
            }
        }
}

/** [color] with less of its opacity when a blur shows through (frosted keeps more than glass). */
@Composable
fun backdropColor(color: Color): Color {
    val blur by WallpaperBackdrop.active.collectAsState()
    return if (blur == null) color else color.copy(alpha = color.alpha * WallpaperBackdrop.fillShare())
}
