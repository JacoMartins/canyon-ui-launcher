package io.github.jacomartins.canyonui.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.jacomartins.canyonui.util.Motion

/**
 * Screens shown one over another inside one activity (Settings' About, a photo folder, the crop
 * screen), moving like the phone's own screens: a deeper one slides in from the end over the
 * one it opens from, which drifts a little the other way; going back plays it in reverse.
 * [depth] says how deep a screen is. Each screen has the page's background, so they cover.
 */
@Composable
fun <T> ScreenSwitch(target: T, depth: (T) -> Int, content: @Composable (T) -> Unit) {
    val page = settingsPageColor()
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (!Motion.enabled()) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                val forward = depth(targetState) >= depth(initialState)
                val move = tween<androidx.compose.ui.unit.IntOffset>(SCREEN_MS, easing = FastOutSlowInEasing)
                val fade = tween<Float>(SCREEN_MS / 2, easing = FastOutSlowInEasing)
                val enter = slideInHorizontally(move) { w -> if (forward) w else -w / 4 } + fadeIn(fade, initialAlpha = if (forward) 1f else 0.6f)
                val exit = slideOutHorizontally(move) { w -> if (forward) -w / 4 else w } + fadeOut(fade, targetAlpha = if (forward) 0.6f else 1f)
                // The deeper screen is on top, coming in or going out.
                (enter togetherWith exit).apply { targetContentZIndex = if (forward) 1f else -1f }
            }
        },
        label = "screen",
    ) { screen ->
        Box(Modifier.fillMaxSize().background(page)) { content(screen) }
    }
}

private const val SCREEN_MS = 380
