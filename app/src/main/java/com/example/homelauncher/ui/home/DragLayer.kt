package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Root view. Owns the vertical drawer gesture so it works the same over the home screen, the dock
 * and the drawer itself:
 * - drawer closed: a mostly-vertical upward drag opens it;
 * - drawer open: a downward drag closes it, but only when the drawer list is scrolled to the top
 *   and the touch did not start on the fast scroller ([DrawerTouchState]);
 * - drawer moving: a touch catches it and the finger takes over.
 * Horizontal page swipes are claimed by [Workspace] through requestDisallowInterceptTouchEvent.
 */
class DragLayer(context: Context) : FrameLayout(context) {

    /** Written by the drawer UI, read here on the main thread. */
    class DrawerTouchState {
        var contentAtTop = true
        var blockDrag = false
    }

    lateinit var drawer: DrawerController

    /** Drag and drop of home items; takes the gesture over once a long-press has armed it. */
    var drag: DragController? = null

    /** False in "Home screen only" mode: there is no drawer to drag. */
    var drawerEnabled = true
    val drawerTouch = DrawerTouchState()

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    /** Where the current gesture went down, in this view's coordinates. */
    val lastDownX get() = downX
    val lastDownY get() = downY

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val d = drag
        if (d != null) {
            if (d.isDragging) return true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
                MotionEvent.ACTION_MOVE -> if (d.armed != null && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                    d.begin(ev.x, ev.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> d.disarm()
            }
            if (d.armed != null) return false
        }
        if (!drawerEnabled && drawer.isClosed) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastY = ev.y
                dragging = false
                track(ev, reset = true)
                if (drawer.isAnimating) {
                    begin(ev)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                val vertical = abs(dy) > slop && abs(dy) > abs(dx) * 1.2f
                if (!vertical) return false
                val wantsDrag = when {
                    drawer.isClosed -> dy < 0
                    drawer.isOpen -> dy > 0 && drawerTouch.contentAtTop && !drawerTouch.blockDrag
                    else -> true
                }
                if (wantsDrag) {
                    begin(ev)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> recycle()
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility") // The drawer also opens from a button and accessibility actions.
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        drag?.takeIf { it.isDragging }?.let { d ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> d.move(ev.x, ev.y)
                MotionEvent.ACTION_UP -> d.drop(ev.x, ev.y)
                MotionEvent.ACTION_CANCEL -> d.cancel()
            }
            return true
        }
        if (!dragging) return super.onTouchEvent(ev)
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                drawer.dragBy(ev.y - lastY)
                lastY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                tracker?.computeCurrentVelocity(1000, maxFling)
                drawer.endDrag(tracker?.yVelocity ?: 0f)
                dragging = false
                recycle()
            }
            MotionEvent.ACTION_CANCEL -> {
                drawer.endDrag(0f)
                dragging = false
                recycle()
            }
        }
        return true
    }

    private fun begin(ev: MotionEvent) {
        dragging = true
        lastY = ev.y
        drawer.startDrag()
    }

    private fun track(ev: MotionEvent, reset: Boolean = false) {
        val t = tracker ?: VelocityTracker.obtain().also { tracker = it }
        if (reset) t.clear()
        t.addMovement(ev)
    }

    private fun recycle() {
        tracker?.recycle()
        tracker = null
    }
}
