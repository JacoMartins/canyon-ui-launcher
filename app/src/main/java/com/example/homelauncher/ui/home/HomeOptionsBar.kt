package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.homelauncher.R
import com.example.homelauncher.util.Motion

/**
 * Shown after a long-press on an empty part of the home screen: a bar at the bottom with
 * Wallpaper and Settings. A tap anywhere outside the bar closes it. (Stage 2 grows this into the
 * full edit mode.)
 */
@SuppressLint("ViewConstructor") // Created in code only.
class HomeOptionsBar(
    context: Context,
    private val onWallpaper: () -> Unit,
    private val onSettings: () -> Unit,
    private val onDismissed: () -> Unit,
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val bar = LinearLayout(context)
    private val background = GradientDrawable().apply { cornerRadius = 28 * d }
    private val labels = ArrayList<TextView>(2)
    private val decelerate = DecelerateInterpolator()

    val isShowing get() = isVisible

    init {
        visibility = GONE
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.background = background
        bar.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        bar.addView(item(R.drawable.ic_wallpaper, R.string.wallpaper) { onWallpaper() })
        bar.addView(item(R.drawable.ic_settings, R.string.settings) { onSettings() })
        addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            (bar.layoutParams as LayoutParams).bottomMargin = bottom + (24 * d).toInt()
            bar.requestLayout()
            insets
        }
    }

    private fun item(icon: Int, label: Int, action: () -> Unit) = TextView(context).apply {
        setText(label)
        textSize = 13f
        gravity = Gravity.CENTER
        compoundDrawablePadding = (6 * d).toInt()
        setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(context, icon)?.mutate(), null, null)
        setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), (12 * d).toInt())
        minWidth = (96 * d).toInt()
        isClickable = true
        isFocusable = true
        background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
        setOnClickListener {
            dismiss()
            action()
        }
        labels += this
    }

    /** Matches the bar to the theme: dark surface with light content in dark mode, and the reverse. */
    fun setColors(surface: Int, content: Int) {
        background.setColor(surface)
        for (t in labels) {
            t.setTextColor(content)
            t.compoundDrawablesRelative.forEach { it?.setTint(content) }
            t.compoundDrawables.forEach { it?.setTint(content) }
        }
    }

    fun show() {
        if (isShowing) return
        visibility = VISIBLE
        if (Motion.enabled()) {
            bar.alpha = 0f
            bar.translationY = 24 * d
            bar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(decelerate).start()
        }
        bar.getChildAt(0)?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    fun dismiss() {
        if (!isShowing) return
        if (Motion.enabled()) {
            bar.animate().alpha(0f).translationY(16 * d).setDuration(150).withEndAction { visibility = GONE }.start()
        } else {
            visibility = GONE
        }
        onDismissed()
    }

    /** Any touch outside the bar closes it (and is not passed to the home screen). */
    @SuppressLint("ClickableViewAccessibility") // Back and the bar's own buttons cover non-touch use.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) dismiss()
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        importantForAccessibility = if (visibility == View.VISIBLE) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}
