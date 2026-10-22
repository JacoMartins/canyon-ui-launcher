package com.example.homelauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.example.homelauncher.R
import com.example.homelauncher.ui.design.Spacing
import com.example.homelauncher.ui.design.buttonRing
import com.example.homelauncher.ui.design.surfaceRing
import com.example.homelauncher.ui.theme.HomeTheme
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
    private val decelerate = DecelerateInterpolator()
    private val bar = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            HomeTheme {
                OptionsBar(
                    onWallpaper = { dismiss(); onWallpaper() },
                    onSettings = { dismiss(); onSettings() },
                )
            }
        }
    }

    val isShowing get() = isVisible

    init {
        visibility = GONE
        addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    fun show() {
        if (isShowing) return
        visibility = VISIBLE
        if (Motion.enabled()) {
            bar.alpha = 0f
            bar.translationY = 24 * d
            bar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(decelerate).start()
        }
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

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        importantForAccessibility = if (visibility == View.VISIBLE) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}

private val BarShape = RoundedCornerShape(32.dp)

@Composable
private fun OptionsBar(onWallpaper: () -> Unit, onSettings: () -> Unit) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = Spacing.lg)
            .shadow(Spacing.sm, BarShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.375f))
            .surfaceRing(cornerRadius = 32.dp),
        shape = BarShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
    ) {
        Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ToolButton(R.drawable.ph_wallpaper, R.string.wallpaper, onWallpaper)
            ToolButton(R.drawable.ph_settings, R.string.settings, onSettings)
        }
    }
}

/** Icon in a ringed tonal circle with a label underneath, as in One UI's edit-mode bar. */
@Composable
private fun ToolButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.smMd, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xsSm),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .buttonRing(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
