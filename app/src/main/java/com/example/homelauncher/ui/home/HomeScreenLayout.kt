package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Home surface: pages, page indicator, dock and the first-run hint, stacked from the top inset
 * to the bottom inset. Laid out by hand so nothing moves when the insets or page count change.
 */
@SuppressLint("ViewConstructor") // Created in code only; never inflated from XML.
class HomeScreenLayout(context: Context, columns: Int, rows: Int, dockSlots: Int) : ViewGroup(context) {

    private val d = resources.displayMetrics.density

    val workspace = Workspace(context)
    val indicator = PageIndicatorView(context)
    val hotseat = CellLayout(context, dockSlots, 1)
    val hint = TextView(context)

    private val dockBackground = GradientDrawable().apply { cornerRadius = 28 * d }

    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0

    private val dockHeight = (96 * d).toInt()
    private val dockMarginH = (12 * d).toInt()
    private val dockMarginBottom = (8 * d).toInt()
    private val indicatorHeight = (24 * d).toInt()
    private val pagePaddingH = (8 * d).toInt()

    var showDockBackground = false

    /** Long-press on an empty part of a page. */
    var onEmptyLongPress: (() -> Unit)? = null

    var gridColumns = columns
        private set
    var gridRows = rows
        private set

    init {
        clipChildren = false
        // One UI shows the dock without a backing surface; the drawable stays for later styles.
        dockBackground.setColor(0)
        hotseat.background = dockBackground
        hotseat.setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
        hint.apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 20 * d }
            alpha = 0f
            visibility = GONE
        }
        addView(workspace)
        addView(indicator)
        addView(hotseat)
        addView(hint)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (bars.top != insetTop || bars.bottom != insetBottom || bars.left != insetLeft || bars.right != insetRight) {
                insetTop = bars.top; insetBottom = bars.bottom; insetLeft = bars.left; insetRight = bars.right
                requestLayout()
            }
            insets
        }
    }

    /** Creates [CellLayout] pages until the workspace has [count]; removes extras. */
    fun setPageCount(count: Int) {
        while (workspace.childCount < count) {
            workspace.addView(CellLayout(context, gridColumns, gridRows).apply {
                setPadding(pagePaddingH, 0, pagePaddingH, 0)
                // Icons consume their own long-press; this only fires on empty cells.
                setOnLongClickListener { v ->
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onEmptyLongPress?.invoke()
                    onEmptyLongPress != null
                }
            })
        }
        while (workspace.childCount > count) workspace.removeViewAt(workspace.childCount - 1)
        indicator.pageCount = count
    }

    fun setGrid(columns: Int, rows: Int, dockSlots: Int) {
        gridColumns = columns
        gridRows = rows
        for (i in 0 until workspace.childCount) workspace.pageAt(i)?.setGridSize(columns, rows)
        hotseat.setGridSize(dockSlots, 1)
    }

    /** Colours that depend on the wallpaper: light or dark labels, dock and hint surfaces. */
    fun setSurfaceColors(dockColor: Int, hintBackground: Int, hintText: Int, indicatorColor: Int) {
        dockBackground.setColor(if (showDockBackground) dockColor else 0)
        (hint.background as GradientDrawable).setColor(hintBackground)
        hint.setTextColor(hintText)
        indicator.color = indicatorColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val contentW = w - insetLeft - insetRight
        val dockTop = h - insetBottom - dockMarginBottom - dockHeight
        val indicatorTop = dockTop - indicatorHeight
        val workspaceH = (indicatorTop - insetTop).coerceAtLeast(0)
        workspace.measure(exactly(contentW), exactly(workspaceH))
        indicator.measure(exactly(contentW), exactly(indicatorHeight))
        hotseat.measure(exactly(contentW - 2 * dockMarginH), exactly(dockHeight))
        hint.measure(MeasureSpec.makeMeasureSpec(contentW - 2 * dockMarginH, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val dockTop = h - insetBottom - dockMarginBottom - dockHeight
        val indicatorTop = dockTop - indicatorHeight
        workspace.layout(insetLeft, insetTop, insetLeft + workspace.measuredWidth, insetTop + workspace.measuredHeight)
        indicator.layout(insetLeft, indicatorTop, insetLeft + indicator.measuredWidth, dockTop)
        val dockLeft = insetLeft + dockMarginH
        hotseat.layout(dockLeft, dockTop, dockLeft + hotseat.measuredWidth, dockTop + dockHeight)
        // Centred in the page area, which is empty on first run (default apps sit in the last row).
        val hintLeft = (w - hint.measuredWidth) / 2
        val hintTop = insetTop + (workspace.measuredHeight - hint.measuredHeight) / 2
        hint.layout(hintLeft, hintTop, hintLeft + hint.measuredWidth, hintTop + hint.measuredHeight)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)
}
