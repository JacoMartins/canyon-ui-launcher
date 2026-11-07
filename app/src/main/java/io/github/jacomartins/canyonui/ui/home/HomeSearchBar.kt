package io.github.jacomartins.canyonui.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import io.github.jacomartins.canyonui.R
import io.github.jacomartins.canyonui.ui.design.DepthRingPainter
import io.github.jacomartins.canyonui.ui.design.WallpaperBackdrop

/**
 * The search bar above the dock, in place of the page dots (a setting): a frosted pill with the
 * search icon, "Search" in the phone's font, and the voice search microphone. Over the wallpaper
 * effect (frosted or glass) it adds a light frost of its own: a faint haze and a fine grain.
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
    private val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(io.github.jacomartins.canyonui.ui.design.Frost.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
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

    fun setColors(labelColor: Int, darkBehind: Boolean) {
        this.darkBehind = darkBehind
        text.color = labelColor
        val filter = PorterDuffColorFilter(labelColor, PorterDuff.Mode.SRC_IN)
        icon.colorFilter = filter
        mic.colorFilter = filter
        fill.color = when {
            plain && darkBehind -> 0xB3101216.toInt()
            plain -> 0xB3F4F4F6.toInt()
            darkBehind -> io.github.jacomartins.canyonui.ui.design.Frost.TINT_DARK
            else -> io.github.jacomartins.canyonui.ui.design.Frost.TINT_LIGHT
        }
        invalidate()
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
        // The frost: a faint haze and a fine grain over the effect.
        canvas.drawRoundRect(rect, r, r, haze)
        canvas.drawRoundRect(rect, r, r, grain)
        ring.draw(canvas, rect, r, darkBehind)
        val size = (22 * d).toInt()
        val left = (18 * d).toInt()
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
        val baseline = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, left + size + 14 * d, baseline, text)
        if (voice) {
            val right = width - (18 * d).toInt()
            mic.setBounds(right - size, top, right, top + size)
            mic.draw(canvas)
        }
    }

    private companion object {
        const val MIC_AREA_DP = 56
    }
}
