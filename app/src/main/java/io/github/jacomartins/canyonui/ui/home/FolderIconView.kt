package io.github.jacomartins.canyonui.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.jacomartins.canyonui.data.AppEntry
import io.github.jacomartins.canyonui.data.FolderEntry
import io.github.jacomartins.canyonui.util.Motion

/**
 * A folder on the home screen or dock.
 * - Normal (1x1): an icon-sized rounded square with a 3x3 preview of its apps; a tap opens it.
 * - Large (2x2): up to nine apps that can be tapped directly; with more than nine, the last cell
 *   shows a small preview of the rest and opens the folder, as does a tap on the name.
 * App icons are drawn as they are (no border or shadow); only the folder backdrop is a surface.
 */
@SuppressLint("ViewConstructor")
class FolderIconView(context: Context, private var style: AppIconView.LabelStyle) : View(context) {

    interface Listener {
        fun onOpenFolder(view: FolderIconView, folder: FolderEntry)
        fun onLaunchFromFolder(view: View, app: AppEntry, bounds: Rect)
    }

    var listener: Listener? = null
    var folder: FolderEntry? = null
        private set

    /** Backdrop colour when the folder has no colour of its own (follows the theme). */
    var defaultColor: Int = 0xD91C1C1E.toInt()
        set(value) { field = value; invalidate() }

    private val dm = resources.displayMetrics
    private val gapPx = AppIconView.LABEL_GAP_DP * dm.density
    private val box = RectF()
    private val cell = Rect()
    private val hit = Rect()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, AppIconView.LABEL_SP, dm)
        textAlign = Paint.Align.CENTER
    }
    private var shownLabel = ""
    private var downX = 0f
    private var downY = 0f
    private val pressInterpolator = DecelerateInterpolator()

    private val isLarge get() = folder?.isLarge == true

    init {
        isClickable = true
        isLongClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
        applyStyle()
    }

    fun bind(entry: FolderEntry) {
        folder = entry
        contentDescription = "${entry.title.ifBlank { "Folder" }}, ${entry.apps.size} apps"
        updateLabel()
        invalidate()
    }

    fun useStyle(newStyle: AppIconView.LabelStyle) {
        if (newStyle === style) return
        style = newStyle
        applyStyle()
    }

    fun applyStyle() {
        textPaint.color = style.color
        val r = if (style.shadowColor == 0) 0f else 3f * dm.density
        textPaint.setShadowLayer(r, 0f, 1f * dm.density, style.shadowColor)
        updateLabel()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateLabel()

    private fun updateLabel() {
        val avail = (width - 4 * dm.density).coerceAtLeast(0f)
        val title = folder?.title.orEmpty()
        shownLabel = if (avail <= 0f) title else TextUtils.ellipsize(title, textPaint, avail, TextUtils.TruncateAt.END).toString()
    }

    /** The backdrop area in view coordinates, also used for the drag image. */
    fun backdropBounds(out: RectF): RectF {
        val showLabel = style.showLabel && folder?.title?.isNotBlank() == true
        val textH = if (showLabel) textPaint.fontSpacing + gapPx else 0f
        if (isLarge) {
            val pad = 10 * dm.density
            val size = minOf(width - 2 * pad, height - textH - 2 * pad)
            val left = (width - size) / 2f
            val top = (height - textH - size) / 2f
            out.set(left, top, left + size, top + size)
        } else {
            val maxIcon = style.maxIconDp * dm.density
            val size = minOf(maxIcon, width * style.iconCellFraction, height - textH)
            val left = (width - size) / 2f
            val top = (height - size - textH) / 2f
            out.set(left, top, left + size, top + size)
        }
        return out
    }

    override fun onDraw(canvas: Canvas) {
        val f = folder ?: return
        backdropBounds(box)
        bgPaint.color = if (f.color != 0) (f.color and 0x00FFFFFF) or (0xD9 shl 24) else defaultColor
        val radius = box.width() * if (isLarge) 0.16f else 0.28f
        canvas.drawRoundRect(box, radius, radius, bgPaint)

        val apps = f.apps
        val slots = 9
        val overflow = apps.size > slots
        val direct = if (overflow) slots - 1 else minOf(apps.size, slots)
        for (i in 0 until direct) {
            cellRect(i, cell)
            canvas.drawBitmap(apps[i].app.icon, null, cell, bitmapPaint)
        }
        if (overflow) {
            // Last cell: 2x2 mini preview of the apps that do not fit.
            cellRect(slots - 1, cell)
            val half = cell.width() / 2
            for (j in 0 until minOf(4, apps.size - direct)) {
                val l = cell.left + (j % 2) * half
                val t = cell.top + (j / 2) * half
                hit.set(l + 1, t + 1, l + half - 1, t + half - 1)
                canvas.drawBitmap(apps[direct + j].app.icon, null, hit, bitmapPaint)
            }
        }
        if (style.showLabel && shownLabel.isNotEmpty()) {
            canvas.drawText(shownLabel, width / 2f, box.bottom + gapPx - textPaint.ascent(), textPaint)
        }
    }

    /** Cell [i] of the 3x3 inner grid, inset so icons have air around them. */
    private fun cellRect(i: Int, out: Rect) {
        val inset = box.width() * if (isLarge) 0.08f else 0.14f
        val inner = box.width() - 2 * inset
        val step = inner / 3f
        val pad = step * if (isLarge) 0.10f else 0.06f
        val col = if (layoutDirection == LAYOUT_DIRECTION_RTL) 2 - i % 3 else i % 3
        val l = box.left + inset + col * step + pad
        val t = box.top + inset + (i / 3) * step + pad
        out.set(l.toInt(), t.toInt(), (l + step - 2 * pad).toInt(), (t + step - 2 * pad).toInt())
    }

    @SuppressLint("ClickableViewAccessibility") // Taps still go through performClick().
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        return super.onTouchEvent(event)
    }

    private fun onTap() {
        val f = folder ?: return
        if (isLarge) {
            for (i in 0 until minOf(f.apps.size, if (f.apps.size > 9) 8 else 9)) {
                cellRect(i, cell)
                if (cell.contains(downX.toInt(), downY.toInt())) {
                    val loc = IntArray(2).also { getLocationOnScreen(it) }
                    hit.set(cell)
                    hit.offset(loc[0], loc[1])
                    listener?.onLaunchFromFolder(this, f.apps[i], hit)
                    return
                }
            }
        }
        listener?.onOpenFolder(this, f)
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed == isPressed) return
        super.setPressed(pressed)
        // Large folders hold tappable apps; scaling the whole block would feel wrong.
        val target = if (pressed && !isLarge) Motion.PRESS_SCALE else 1f
        if (Motion.enabled()) {
            animate().scaleX(target).scaleY(target).setDuration(Motion.PRESS_DURATION_MS).setInterpolator(pressInterpolator).start()
        } else {
            scaleX = target
            scaleY = target
        }
    }
}
