package io.github.jacomartins.canyonui.ui.design

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import androidx.annotation.RequiresApi
import java.util.WeakHashMap

/**
 * Liquid glass for the View-based surfaces (folders, edit pages, widget cards), where Haze (a
 * Compose library) cannot draw: an AGSL lens over the lightly blurred wallpaper. Near the rim
 * the glass bends what is behind it inward (the edge magnifies, like a thick pane), and a light
 * from above brightens the top rim with a weaker bounce at the bottom. Android 13+.
 */
@RequiresApi(33)
internal object GlassShader {

    private const val SRC = """
        uniform shader image;
        uniform float2 origin;
        uniform float2 size;
        uniform float radius;
        uniform float bend;
        uniform float rim;

        float sdBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 xy) {
            float2 hs = size * 0.5;
            float2 p = xy - origin - hs;
            float r = min(radius, min(hs.x, hs.y));
            float d = sdBox(p, hs, r);
            float band = max(min(hs.x, hs.y) * 0.35, 1.0);
            float t = clamp(1.0 + d / band, 0.0, 1.0);
            float2 e = float2(0.75, 0.0);
            float2 g = float2(sdBox(p + e.xy, hs, r) - sdBox(p - e.xy, hs, r),
                              sdBox(p + e.yx, hs, r) - sdBox(p - e.yx, hs, r));
            float2 n = g / max(length(g), 0.0001);
            float2 s = xy - n * (t * t * bend);
            half4 c = image.eval(s);
            float lit = smoothstep(0.6, 1.0, t) * (0.5 - 0.5 * n.y);
            float bounce = smoothstep(0.75, 1.0, t) * max(n.y, 0.0) * 0.35;
            c.rgb = c.rgb + half3(rim * (lit + bounce));
            return half4(c.rgb, 1.0);
        }
    """

    /** One shader and paint per view: a recorded draw keeps the values it was drawn with. */
    private class Holder(val shader: RuntimeShader, val paint: Paint) {
        var image: Bitmap? = null
        var imageShader: BitmapShader? = null
    }

    private val holders = WeakHashMap<View, Holder>()
    private val matrix = Matrix()
    private val bounds = RectF()

    /**
     * Draws the glass inside [clip] on [view]'s canvas. [bitmap] is the blurred wallpaper (drawn
     * stretched to [screenW] x [screenH]); [loc] and [scale] place the view on screen.
     */
    fun draw(
        canvas: Canvas,
        view: View,
        clip: Path,
        radius: Float,
        bitmap: Bitmap,
        screenW: Int,
        screenH: Int,
        loc: IntArray,
        scale: Float,
    ): Boolean {
        if (broken) return false
        val h = holders[view] ?: try {
            Holder(RuntimeShader(SRC), Paint(Paint.ANTI_ALIAS_FLAG)).also { holders[view] = it }
        } catch (e: IllegalArgumentException) {
            // The GPU's shader compiler refused it: fall back to the plain blur everywhere.
            android.util.Log.w("GlassShader", "Glass shader unavailable", e)
            broken = true
            return false
        }
        if (h.image !== bitmap) {
            h.image = bitmap
            h.imageShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val image = h.imageShader ?: return false
        // Bitmap pixels -> screen -> this view's coordinates.
        matrix.reset()
        matrix.postScale(screenW / bitmap.width.toFloat(), screenH / bitmap.height.toFloat())
        matrix.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
        matrix.postScale(1f / scale, 1f / scale)
        image.setLocalMatrix(matrix)
        clip.computeBounds(bounds, true)
        val d = view.resources.displayMetrics.density
        val s = h.shader
        s.setInputShader("image", image)
        s.setFloatUniform("origin", bounds.left, bounds.top)
        s.setFloatUniform("size", bounds.width(), bounds.height())
        s.setFloatUniform("radius", if (radius >= 0f) radius else minOf(bounds.width(), bounds.height()) * 0.25f)
        s.setFloatUniform("bend", BEND_DP * d)
        s.setFloatUniform("rim", RIM)
        h.paint.shader = s
        canvas.drawPath(clip, h.paint)
        return true
    }

    private var broken = false

    private const val BEND_DP = 7f
    private const val RIM = 0.22f
}
