package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.data.DragItem
import com.example.homelauncher.data.HomeState
import com.example.homelauncher.model.Cell
import com.example.homelauncher.model.Container
import com.example.homelauncher.model.DropAction
import com.example.homelauncher.model.DropLogic
import com.example.homelauncher.util.Motion
import kotlin.math.abs

/** What is being dragged and how to draw it. */
class DragSource(
    val item: DragItem,
    val type: Int,
    val spanX: Int,
    val spanY: Int,
    val image: Bitmap,
    /** Image size and the finger's offset inside it, in screen pixels. */
    val width: Int,
    val height: Int,
    val touchX: Float,
    val touchY: Float,
    /** Hidden while dragging; null for items coming from the drawer or a folder popup. */
    val view: View?,
    val app: AppInfo?,
)

/** Where a drag ended. */
sealed interface DropResult {
    data class OnGrid(val action: DropAction) : DropResult
    data object Cancelled : DropResult
}

/** The icon under the finger. Moves by translation only. */
@SuppressLint("ViewConstructor")
class DragView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        dst.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}

/**
 * Drives a drag from a long-press to the drop. Hit-tests the dock and the current page; asks [DropLogic] what a release would do; shows the drop hint; ticks a haptic when the
 * target changes; flips pages when the finger rests at a screen edge.
 */
class DragController(
    private val root: FrameLayout,
    private val home: HomeScreenLayout,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun state(): HomeState?
        fun onDragStarted(source: DragSource)
        fun onDropped(source: DragSource, result: DropResult)
    }

    var armed: DragSource? = null
        private set
    var source: DragSource? = null
        private set
    val isDragging get() = source != null

    private var dragView: DragView? = null
    private val pt = FloatArray(2)
    private val cellOut = IntArray(2)
    private var lastAction: DropAction? = null
    private var lastHintCell: CellLayout? = null
    private var lastX = 0f
    private var lastY = 0f
    // What the last drop decision was based on; it is only recomputed when one of these changes.
    private var lastKey = Long.MIN_VALUE
    private val edge = 28 * root.resources.displayMetrics.density
    private var pageFlipPending = false
    private val decelerate = DecelerateInterpolator()

    private val pageFlip = Runnable {
        pageFlipPending = false
        if (!isDragging) return@Runnable
        val ws = home.workspace
        val rtl = ws.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val forward = (lastX > root.width - edge) != rtl
        val next = ws.currentPage + if (forward) 1 else -1
        if (next in 0 until ws.childCount) {
            ws.snapToPage(next)
            root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            update(lastX, lastY)
            scheduleFlipIfAtEdge()
        }
    }

    /** Called on long-press: a drag starts if the finger then moves. */
    fun arm(source: DragSource) {
        armed = source
    }

    fun disarm() {
        armed = null
    }

    fun begin(x: Float, y: Float) {
        val s = armed ?: return
        armed = null
        source = s
        callbacks.onDragStarted(s)
        s.view?.visibility = View.INVISIBLE
        val dv = DragView(root.context, s.image)
        root.addView(dv, FrameLayout.LayoutParams(s.width, s.height))
        dragView = dv
        dv.pivotX = s.touchX
        dv.pivotY = s.touchY
        if (Motion.enabled()) {
            dv.scaleX = 1f; dv.scaleY = 1f
            dv.animate().scaleX(DRAG_SCALE).scaleY(DRAG_SCALE).setDuration(120).start()
        }
        update(x, y)
    }

    fun move(x: Float, y: Float) {
        if (!isDragging) return
        update(x, y)
        scheduleFlipIfAtEdge()
    }

    fun drop(x: Float, y: Float) {
        val s = source ?: return
        update(x, y)
        val result = when {
            lastAction != null && lastAction != DropAction.Reject -> DropResult.OnGrid(lastAction!!)
            else -> DropResult.Cancelled
        }
        finish(s, result)
    }

    fun cancel() {
        val s = source ?: return
        finish(s, DropResult.Cancelled)
    }

    private fun finish(s: DragSource, result: DropResult) {
        root.removeCallbacks(pageFlip)
        pageFlipPending = false
        lastHintCell?.clearDropHint()
        lastHintCell = null
        val dv = dragView
        dragView = null
        source = null
        lastAction = null
        lastKey = Long.MIN_VALUE
        if (dv != null) {
            if (Motion.enabled()) {
                dv.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(decelerate)
                    .withEndAction { root.removeView(dv) }.start()
            } else {
                root.removeView(dv)
            }
        }
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        callbacks.onDropped(s, result)
    }

    private fun update(x: Float, y: Float) {
        val s = source ?: return
        lastX = x
        lastY = y
        dragView?.let {
            it.translationX = x - s.touchX
            it.translationY = y - s.touchY
        }

        val st = callbacks.state() ?: return
        // Dock first, then the page in view.
        val overDock = isOver(home.hotseat, x, y)
        val cellLayout: CellLayout = if (overDock) home.hotseat else home.workspace.pageAt(home.workspace.currentPage) ?: return
        val container = if (overDock) Container.DOCK else Container.DESKTOP
        val pageIndex = if (overDock) 0 else home.workspace.currentPage

        // Finger cell and whether the finger is near its centre (folder intent).
        pt[0] = x; pt[1] = y
        ViewMap.rootToLocal(root, cellLayout, pt)
        val fx = pt[0]
        val fy = pt[1]
        if (!cellLayout.cellAt(fx, fy, cellOut)) {
            lastHintCell?.clearDropHint()
            lastHintCell = null
            lastAction = DropAction.Reject
            return
        }
        val finger = Cell(cellOut[0], cellOut[1])
        val cw = cellLayout.cellWidth.toFloat()
        val ch = cellLayout.cellHeight.toFloat()
        val cx = cellLayout.cellLeft(finger.x) + cw / 2f
        val cy = cellLayout.cellTop(finger.y) + ch / 2f
        val centerHit = abs(fx - cx) < cw * 0.28f && abs(fy - cy) < ch * 0.28f

        // For spans, the top-left cell follows the image's top-left corner.
        val desired = if (s.spanX == 1 && s.spanY == 1) {
            finger
        } else {
            val scale = ViewMap.scaleOf(root, cellLayout)
            Cell(
                (finger.x - ((s.touchX / scale) / cw).toInt()).coerceAtLeast(0),
                (finger.y - ((s.touchY / scale) / ch).toInt()).coerceAtLeast(0),
            )
        }
        val key = ((container.toLong() * 64 + pageIndex) * 64 + finger.x) * 4096L + finger.y * 64L + desired.x * 8L + desired.y +
            (if (centerHit) 1L shl 40 else 0L) + (System.identityHashCode(st).toLong() shl 41)
        if (key == lastKey && lastHintCell === cellLayout) return
        lastKey = key
        val columns = if (overDock) st.dockSlots else st.columns
        val rows = if (overDock) 1 else st.rows
        val draggedId = (s.item as? DragItem.Existing)?.id
        val action = DropLogic.decide(
            container, pageIndex, columns, rows, st.placedIn(container, pageIndex),
            desired, finger, centerHit, draggedId, s.type, s.spanX, s.spanY,
        )
        if (action != lastAction) {
            lastAction = action
            if (lastHintCell !== cellLayout) lastHintCell?.clearDropHint()
            lastHintCell = cellLayout
            when (action) {
                is DropAction.Move -> cellLayout.showDropHint(action.x, action.y, s.spanX, s.spanY, folder = false)
                is DropAction.CreateFolder, is DropAction.AddToFolder -> {
                    val id = (action as? DropAction.CreateFolder)?.targetId ?: (action as DropAction.AddToFolder).folderId
                    val t = st.entry(id)?.placed
                    if (t != null) cellLayout.showDropHint(t.x, t.y, t.spanX, t.spanY, folder = t.spanX == 1)
                }
                DropAction.Reject -> cellLayout.clearDropHint()
            }
            if (action != DropAction.Reject) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun isOver(v: View, x: Float, y: Float): Boolean {
        if (!v.isShown) return false
        pt[0] = x; pt[1] = y
        ViewMap.rootToLocal(root, v, pt)
        return pt[0] >= 0 && pt[1] >= 0 && pt[0] < v.width && pt[1] < v.height
    }

    private fun scheduleFlipIfAtEdge() {
        val atEdge = lastX < edge || lastX > root.width - edge
        if (atEdge && !pageFlipPending) {
            pageFlipPending = true
            root.postDelayed(pageFlip, PAGE_FLIP_DELAY_MS)
        } else if (!atEdge && pageFlipPending) {
            root.removeCallbacks(pageFlip)
            pageFlipPending = false
        }
    }

    companion object {
        private const val DRAG_SCALE = 1.08f
        private const val PAGE_FLIP_DELAY_MS = 550L
    }
}
