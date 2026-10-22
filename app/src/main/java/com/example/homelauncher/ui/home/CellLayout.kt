package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isGone

/**
 * A fixed grid of [columns] x [rows] cells. Children carry their cell in [LayoutParams] and are
 * measured to exactly their span. Columns are mirrored in right-to-left layouts.
 */
@SuppressLint("ViewConstructor") // Created in code only; never inflated from XML.
class CellLayout(context: Context, columns: Int, rows: Int) : ViewGroup(context) {

    var columns = columns
        private set
    var rows = rows
        private set

    class LayoutParams(var cellX: Int, var cellY: Int, var spanX: Int = 1, var spanY: Int = 1) :
        ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    fun setGridSize(columns: Int, rows: Int) {
        if (columns == this.columns && rows == this.rows) return
        this.columns = columns
        this.rows = rows
        requestLayout()
    }

    val cellWidth: Int get() = (width - paddingLeft - paddingRight) / columns
    val cellHeight: Int get() = (height - paddingTop - paddingBottom) / rows

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight) / columns
        val ch = (h - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            val lp = child.layoutParams as LayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(cw * lp.spanX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch * lp.spanY, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellWidth
        val ch = cellHeight
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            val lp = child.layoutParams as LayoutParams
            val col = if (rtl) columns - lp.cellX - lp.spanX else lp.cellX
            val left = paddingLeft + col * cw
            val top = paddingTop + lp.cellY * ch
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LayoutParams
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun shouldDelayChildPressedState() = false
}
