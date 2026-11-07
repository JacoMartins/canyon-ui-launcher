package io.github.jacomartins.canyonui.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import io.github.jacomartins.canyonui.R
import io.github.jacomartins.canyonui.ui.design.DepthRingPainter
import io.github.jacomartins.canyonui.ui.design.WallpaperBackdrop

/**
 * The search bar above the dock, in place of the page dots (a setting): a frosted pill with the
 * search icon, "Search" in the phone's font, and the voice search microphone. Over the wallpaper
 * effect (frosted or glass) it adds a light frost of its own: a faint haze.
 *
 * [plain]: drawn without the wallpaper effect (the copy that travels into the Apps screen's
 * search field while it slides up).
 */
@SuppressLint("ViewConstructor") // Created in code only; never inflated from XML.
class HomeSearchBar(context: Context, private val plain: Boolean = false) : View(context) {
    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val path = Path()
    private val ring = DepthRingPainter(d)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = io.github.jacomartins.canyonui.ui.design.Frost.HAZE }
    // The field's text: 16sp in the phone's font, medium weight (the theme's titleMedium).
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 500, false)
    }
    private val icon = ContextCompat.getDrawable(context, R.drawable.ph_search)!!.mutate()
    private val mic = ContextCompat.getDrawable(context, R.drawable.ph_mic)!!.mutate()
    private val label = resources.getString(R.string.search)

    /** Shows the microphone (when the phone has voice search). */
    var voice = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    /** Light labels (dark wallpaper behind) or dark ones. */
    var darkBehind = true
        private set

    private var lastTouchX = -1f

    private var styled = false

    /** Colours by the wallpaper behind, until [setStyle] gives the search field's own. */
    fun setColors(labelColor: Int, darkBehind: Boolean) {
        this.darkBehind = darkBehind
        if (styled) { invalidate(); return }
        applyContent(labelColor)
        fill.color = when {
            plain && darkBehind -> 0xB3101216.toInt()
            plain -> 0xB3F4F4F6.toInt()
            darkBehind -> io.github.jacomartins.canyonui.ui.design.Frost.TINT_DARK
            else -> io.github.jacomartins.canyonui.ui.design.Frost.TINT_LIGHT
        }
        invalidate()
    }

    /**
     * The Apps screen's search field's look ([fill], [content], ARGB), a little more
     * see-through ([OPACITY]): the same bar on home and in the screen.
     */
    fun setStyle(fill: Int, content: Int) {
        styled = true
        val a = ((fill ushr 24) * OPACITY).toInt().coerceIn(0, 255)
        this.fill.color = (a shl 24) or (fill and 0xFFFFFF)
        applyContent(content)
        invalidate()
    }

    private fun applyContent(color: Int) {
        text.color = color
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        icon.colorFilter = filter
        mic.colorFilter = filter
    }

    init {
        contentDescription = label
        isClickable = !plain
        isFocusable = !plain
        if (plain) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setColors(0xFFFFFFFF.toInt(), true)
        if (!plain) WallpaperBackdrop.track(this)
    }

    /** Whether the last tap was on the microphone. */
    fun tappedMic(): Boolean = voice && lastTouchX >= width - MIC_AREA_DP * d

    @SuppressLint("ClickableViewAccessibility") // Only notes where the tap was; the click is the usual one.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) lastTouchX = event.x
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        path.reset()
        path.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        if (!plain) WallpaperBackdrop.draw(canvas, this, path, r)
        canvas.drawRoundRect(rect, r, r, fill)
        // The frost: a faint haze over the effect.
        canvas.drawRoundRect(rect, r, r, haze)
        ring.draw(canvas, rect, r, darkBehind)
        // The field's layout: a 20dp icon 20dp in, the text 12dp after it, the microphone 18dp
        // from the end.
        val size = (20 * d).toInt()
        val left = (20 * d).toInt()
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
        val baseline = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, left + size + 12 * d, baseline, text)
        if (voice) {
            val right = width - (18 * d).toInt()
            mic.setBounds(right - size, top, right, top + size)
            mic.draw(canvas)
        }
    }

    private companion object {
        const val MIC_AREA_DP = 56

        /** Home's bar keeps this much of the field's opacity. */
        const val OPACITY = 0.75f
    }
}
