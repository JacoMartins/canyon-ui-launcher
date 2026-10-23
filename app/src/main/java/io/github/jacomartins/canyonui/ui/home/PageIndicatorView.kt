package io.github.jacomartins.canyonui.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import io.github.jacomartins.canyonui.R
import kotlin.math.abs

/**
 * The page indicator in Phosphor Fill icons: a circle per page and a house for the home page. The
 * page in view is fully opaque and the others faint; the opacity follows the scroll position, so
 * the indicator tracks the finger instead of jumping at the end of a swipe.
 */
class PageIndicatorView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val dot = (9 * d).toInt()
    private val house = (12 * d).toInt()
    private val gap = 7 * d

    private val circleFill = icon(R.drawable.ph_circle_fill)
    private val homeFill = icon(R.drawable.ph_house_fill)

    private fun icon(res: Int): Drawable = ContextCompat.getDrawable(context, res)!!.mutate()

    var pageCount = 1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private var position = 0f

    var color: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            val filter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_IN)
            listOf(circleFill, homeFill).forEach { it.colorFilter = filter }
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        color = color
    }

    fun setPosition(pages: Float) {
        if (position == pages) return
        position = pages
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize((24 * d).toInt(), heightMeasureSpec),
        )
    }

    private companion object {
        /** Opacity of the pages not in view. */
        const val INACTIVE = 0.35f
    }

    private fun draw(canvas: Canvas, icon: Drawable, cx: Float, cy: Float, size: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        val l = (cx - size / 2f).toInt()
        val t = (cy - size / 2f).toInt()
        icon.setBounds(l, t, l + size, t + size)
        icon.alpha = (alpha * 255).toInt()
        icon.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 1) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val total = (pageCount - 1) * (dot + gap) + house
        var x = (width - total) / 2f
        val cy = height / 2f
        for (slot in 0 until pageCount) {
            val page = if (rtl) pageCount - 1 - slot else slot
            // 1 when this page is in view, falling to 0 one page away.
            val t = (1f - abs(position - page)).coerceIn(0f, 1f)
            val size = if (page == 0) house else dot
            val cx = x + size / 2f
            draw(canvas, if (page == 0) homeFill else circleFill, cx, cy, size, INACTIVE + (1f - INACTIVE) * t)
            x += size + gap
        }
    }
}
