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
import kotlin.math.roundToInt
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
    private var solid = false
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = io.github.jacomartins.canyonui.ui.design.Frost.HAZE }
    // The field's text: 16sp in the phone's font, medium weight (the theme's titleMedium).
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 500, false)
    }
    // Bold here (asked for, the one exception to the regular icons), as in the search field.
    private val icon = ContextCompat.getDrawable(context, R.drawable.ph_search_bold)!!.mutate()
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
    fun setStyle(fill: Int, content: Int, dark: Boolean) {
        styled = true
        val (a0, a1) = io.github.jacomartins.canyonui.ui.design.SearchIconGradient.of(dark)
        if (a0 != gradStart || a1 != gradEnd) {
            gradStart = a0
            gradEnd = a1
            gradSize = -1
        }
        // Dark mode's bar is see-through black like a folder: the field's own opacity, no frost.
        solid = dark
        val a = if (dark) fill ushr 24 else ((fill ushr 24) * OPACITY).toInt().coerceIn(0, 255)
        this.fill.color = (a shl 24) or (fill and 0xFFFFFF)
        applyContent(content)
        invalidate()
    }

    private fun applyContent(color: Int) {
        text.color = color
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        mic.colorFilter = filter
    }

    // The search icon: white, then the gradient laid over it (kept to its shape).
    private var gradStart = io.github.jacomartins.canyonui.ui.design.SearchIconGradient.DARK_START
    private var gradEnd = io.github.jacomartins.canyonui.ui.design.SearchIconGradient.DARK_END
    private var gradSize = -1
    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    private fun drawSearchIcon(canvas: Canvas, left: Int, top: Int, size: Int) {
        icon.colorFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
        icon.setBounds(left, top, left + size, top + size)
        if (gradSize != size) {
            gradSize = size
            gradPaint.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), gradStart, gradEnd, android.graphics.Shader.TileMode.CLAMP)
        }
        val layer = canvas.saveLayer(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat(), null)
        icon.draw(canvas)
        canvas.translate(left.toFloat(), top.toFloat())
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradPaint)
        canvas.restoreToCount(layer)
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
        if (!solid) canvas.drawRoundRect(rect, r, r, haze)
        ring.draw(canvas, rect, r, darkBehind)
        // The field's layout: a 24dp search icon 18dp in (in its gradient), the text 12dp after
        // it, a 20dp microphone 18dp from the end.
        // Rounded to whole pixels as the search field's layout rounds dp (to the nearest, not
        // down), so the two bars line up to the pixel.
        val iconSize = (24 * d).roundToInt()
        val left = (18 * d).roundToInt()
        drawSearchIcon(canvas, left, (height - iconSize) / 2, iconSize)
        val baseline = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, (left + iconSize + (12 * d).roundToInt()).toFloat(), baseline, text)
        if (voice) {
            val size = (20 * d).roundToInt()
            val top = (height - size) / 2
            val right = width - (18 * d).toInt()
            mic.setBounds(right - size, top, right, top + size)
            mic.draw(canvas)
        }
    }

    private companion object {
        const val MIC_AREA_DP = 56

        /** Home's bar keeps this much of the field's opacity. */
        const val OPACITY = io.github.jacomartins.canyonui.ui.design.HOME_BAR_OPACITY
    }
}
