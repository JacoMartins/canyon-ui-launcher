package io.github.jacomartins.canyonui.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import io.github.jacomartins.canyonui.R
import io.github.jacomartins.canyonui.ui.design.DepthRingPainter
import io.github.jacomartins.canyonui.ui.design.WallpaperBackdrop

/**
 * The search bar above the dock, in place of the page dots (a setting): a frosted pill with the
 * search icon and "Search", in the phone's font. Tapping it opens search.
 */
class HomeSearchBar(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val path = Path()
    private val ring = DepthRingPainter(d)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics) }
    private val icon = ContextCompat.getDrawable(context, R.drawable.ph_search)!!.mutate()
    private val label = resources.getString(R.string.search)

    /** Light labels (dark wallpaper behind) or dark ones. */
    var darkBehind = true
        private set

    fun setColors(labelColor: Int, darkBehind: Boolean) {
        this.darkBehind = darkBehind
        text.color = labelColor
        icon.colorFilter = PorterDuffColorFilter(labelColor, PorterDuff.Mode.SRC_IN)
        fill.color = if (darkBehind) 0x33000000 else 0x40FFFFFF
        invalidate()
    }

    init {
        contentDescription = label
        isClickable = true
        isFocusable = true
        setColors(0xFFFFFFFF.toInt(), true)
        WallpaperBackdrop.track(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        path.reset()
        path.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        WallpaperBackdrop.draw(canvas, this, path, r)
        canvas.drawRoundRect(rect, r, r, fill)
        ring.draw(canvas, rect, r, darkBehind)
        val size = (22 * d).toInt()
        val left = (18 * d).toInt()
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
        val baseline = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, left + size + 14 * d, baseline, text)
    }
}
