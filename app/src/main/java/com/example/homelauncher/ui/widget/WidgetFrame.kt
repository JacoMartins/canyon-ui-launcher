package com.example.homelauncher.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * The design system's shape for every widget on home: corners rounded to [MAX_RADIUS_DP], or a
 * full pill for widgets one cell wide or one row tall, whatever shape the widget draws itself;
 * and, optionally, OneDepth's translucent ring along the edge. Applied to the top-level view (a
 * widget, a stack, a placeholder); widgets inside a stack are left plain.
 *
 * Many widgets draw their visible card smaller than the area they are given (a search bar with
 * transparent margins). The frame follows that card when it can be found: the first view down
 * a single-child chain that has a background.
 */
object WidgetFrame {
    const val MAX_RADIUS_DP = 26f

    /** Gap kept around a widget inside its cells, per side (One UI 8.5 keeps about 10.5dp). */
    const val INSET_DP = 10f

    /** [pill]: a fully rounded end (one cell wide or one row tall). */
    fun apply(view: View, ring: Boolean, pill: Boolean) {
        val d = view.resources.displayMetrics.density
        val frame = view.outlineProvider as? Frame ?: Frame(MAX_RADIUS_DP * d).also { view.outlineProvider = it }
        view.clipToOutline = true
        if (frame.pill != pill) {
            frame.pill = pill
            view.invalidateOutline()
            view.foreground?.invalidateSelf()
        }
        val fg = view.foreground
        if (ring && fg !is Ring) view.foreground = Ring(frame, d)
        if (!ring && fg is Ring) view.foreground = null
        refresh(view)
    }

    fun clear(view: View) {
        if (view.outlineProvider is Frame) view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = false
        if (view.foreground is Ring) view.foreground = null
    }

    /** Re-finds the widget's visible card after a layout pass or a new widget layout. */
    fun refresh(view: View) {
        val frame = view.outlineProvider as? Frame ?: return
        val old = Rect(frame.content)
        contentBounds(view, frame.content)
        if (old != frame.content) {
            view.invalidateOutline()
            view.foreground?.invalidateSelf()
        }
    }

    /**
     * A drag image of a framed widget: drawing a view into a bitmap skips its outline clip, so the
     * same rounded shape is cut out here. [local] is the part of the view in the bitmap.
     */
    fun clipSnapshot(view: View, bitmap: Bitmap, local: RectF) {
        val frame = view.outlineProvider as? Frame ?: return
        val c = Canvas(bitmap)
        c.translate(-local.left, -local.top)
        val r = frame.radius()
        val path = Path().apply {
            addRect(RectF(local), Path.Direction.CW)
            addRoundRect(RectF(frame.content), r, r, Path.Direction.CCW)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    private fun contentBounds(view: View, out: Rect) {
        out.set(0, 0, view.width, view.height)
        if (view !is LauncherWidgetHostView) return
        var cur: View = view
        var dx = 0
        var dy = 0
        while (cur is ViewGroup && cur.childCount == 1) {
            val c = cur.getChildAt(0)
            dx += c.left
            dy += c.top
            if (c.background != null) {
                val w = c.width
                val h = c.height
                // Only a substantial card counts; a small badge with a background must not shrink the frame.
                if (w * h >= view.width * view.height * 2 / 5) out.set(dx, dy, dx + w, dy + h)
                return
            }
            cur = c
        }
    }

    private class Frame(private val max: Float) : ViewOutlineProvider() {
        var pill = false
        val content = Rect()

        fun radius(): Float {
            val half = min(content.width(), content.height()) / 2f
            return if (pill) half else min(max, half)
        }

        override fun getOutline(view: View, outline: Outline) {
            if (content.isEmpty) content.set(0, 0, view.width, view.height)
            outline.setRoundRect(content, radius())
        }
    }

    /** A 1dp translucent white line inside the frame's edge. */
    private class Ring(private val frame: Frame, density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = RING_COLOR
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val c = if (frame.content.isEmpty) bounds else frame.content
            val half = paint.strokeWidth / 2f
            rect.set(c.left + half, c.top + half, c.right - half, c.bottom - half)
            val r = frame.radius() - half
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
