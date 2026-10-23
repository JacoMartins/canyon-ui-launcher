package com.example.homelauncher.ui.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * The design system's shape for every widget on home: corners rounded to [MAX_RADIUS_DP], or a
 * pill when the widget is shorter than twice that, whatever shape the widget draws itself; and,
 * optionally, OneDepth's translucent ring along the edge. Applied to the top-level view (a
 * widget, a stack, a placeholder); widgets inside a stack are left plain.
 */
object WidgetFrame {
    const val MAX_RADIUS_DP = 26f

    /** Gap kept around a widget inside its cells, per side (One UI 8.5 keeps about 10.5dp). */
    const val INSET_DP = 10f

    fun apply(view: View, ring: Boolean) {
        val d = view.resources.displayMetrics.density
        val maxRadius = MAX_RADIUS_DP * d
        if (view.outlineProvider !is Rounded) view.outlineProvider = Rounded(maxRadius)
        view.clipToOutline = true
        val fg = view.foreground
        if (ring && fg !is Ring) view.foreground = Ring(maxRadius, d)
        if (!ring && fg is Ring) view.foreground = null
    }

    fun clear(view: View) {
        if (view.outlineProvider is Rounded) view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = false
        if (view.foreground is Ring) view.foreground = null
    }

    private fun radius(w: Int, h: Int, max: Float) = min(max, min(w, h) / 2f)

    private class Rounded(private val max: Float) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius(view.width, view.height, max))
        }
    }

    /** A 1dp translucent white line inside the edge. */
    private class Ring(private val max: Float, density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = RING_COLOR
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val half = paint.strokeWidth / 2f
            rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half)
            val r = radius(b.width(), b.height(), max) - half
            canvas.drawRoundRect(rect, r, r, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private const val RING_COLOR = 0x59FFFFFF
}
