package com.example.homelauncher.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

/**
 * Dots for the home pages. The active dot stretches into a pill and slides with the scroll
 * position, so it tracks the finger rather than jumping at the end of a swipe.
 */
class PageIndicatorView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val dot = 6 * d
    private val gap = 8 * d
    private val activeWidth = 16 * d
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var pageCount = 1
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private var position = 0f

    var color: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; invalidate() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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

    private val housePath = android.graphics.Path()

    /** The first page is marked with a small house, as on One UI. */
    private fun drawHome(canvas: Canvas, cx: Float, cy: Float) {
        val s = 4.5f * d
        housePath.rewind()
        housePath.moveTo(cx, cy - s)
        housePath.lineTo(cx + s, cy - s * 0.1f)
        housePath.lineTo(cx + s * 0.75f, cy - s * 0.1f)
        housePath.lineTo(cx + s * 0.75f, cy + s)
        housePath.lineTo(cx - s * 0.75f, cy + s)
        housePath.lineTo(cx - s * 0.75f, cy - s * 0.1f)
        housePath.lineTo(cx - s, cy - s * 0.1f)
        housePath.close()
        canvas.drawPath(housePath, paint)
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 1) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // The stretched widths of neighbouring dots always sum to one extra pill, so the row
        // keeps a constant width and stays centred while it animates.
        val total = (pageCount - 1) * (dot + gap) + activeWidth
        var x = (width - total) / 2f
        val cy = height / 2f
        paint.color = color
        for (slot in 0 until pageCount) {
            val page = if (rtl) pageCount - 1 - slot else slot
            // 1 when this dot is the current page, falling to 0 one page away.
            val t = (1f - abs(position - page)).coerceIn(0f, 1f)
            val w = dot + (activeWidth - dot) * t
            paint.alpha = (90 + 165 * t).toInt()
            if (page == 0) {
                drawHome(canvas, x + w / 2, cy)
            } else {
                rect.set(x, cy - dot / 2, x + w, cy + dot / 2)
                canvas.drawRoundRect(rect, dot / 2, dot / 2, paint)
            }
            x += w + gap
        }
    }
}
