package com.example.homelauncher.diagnostics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.homelauncher.BuildConfigInfo
import com.example.homelauncher.ui.theme.HomeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * Stage 0 test panel. Not part of the final launcher: it exists so the toolchain, Haze and
 * device-only questions can be verified on the phone before feature work starts.
 */
@Composable
fun Stage0Panel() {
    HomeTheme {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header()
            InsetsCard()
            GlassDemoCard()
            WallpaperCard()
            IconCard()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Header() {
    val context = LocalContext.current
    Section("Home launcher · Stage 0") {
        Text(
            "The home page is intentionally empty. This panel checks the build, insets, glass " +
                "effects and device capabilities. Version ${BuildConfigInfo.versionName(context)}.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = {
            try {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }) { Text("Change default home app") }
    }
}

@Composable
private fun InsetsCard() {
    val density = LocalDensity.current
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    Section("Insets (px)") {
        Text(
            "top ${insets.getTop(density)} · bottom ${insets.getBottom(density)} · " +
                "left ${insets.getLeft(density, dir)} · right ${insets.getRight(density, dir)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Nothing on this screen should sit under the status bar, the camera cutout or the " +
                "gesture bar.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GlassDemoCard() {
    val hazeState = rememberHazeState()
    var animate by remember { mutableStateOf(false) }
    var frameMs by remember { mutableFloatStateOf(0f) }

    // Average frame interval over a rolling window, only while the backdrop animates.
    if (animate) {
        LaunchedEffect(Unit) {
            val window = LongArray(60)
            var i = 0
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                window[i % window.size] = now - last
                last = now
                i++
                if (i % 30 == 0) {
                    val n = minOf(i, window.size)
                    var sum = 0L
                    for (k in 0 until n) sum += window[k]
                    frameMs = sum / n / 1_000_000f
                }
            }
        }
    }

    Section("Glass effects (Haze 2.0.1)") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            DemoBackdrop(hazeState, animate, Modifier.fillMaxSize())
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatChip(Modifier.weight(1f))
                FrostedChip(hazeState, Modifier.weight(1f))
                LiquidChip(hazeState, Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Animate backdrop", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = animate, onCheckedChange = { animate = it; frameMs = 0f })
        }
        if (animate) {
            Text(
                "Avg frame interval: ${"%.1f".format(frameMs)} ms (16.7 ms = 60 fps)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Colourful Compose-drawn content that the chips blur and refract. */
@Composable
private fun DemoBackdrop(hazeState: HazeState, animate: Boolean, modifier: Modifier) {
    val shift = if (animate) {
        val t = rememberInfiniteTransition(label = "backdrop")
        t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
            label = "shift",
        ).value
    } else {
        0.3f
    }
    Canvas(modifier.hazeSource(hazeState)) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF6A1B9A), Color(0xFFEF6C00))))
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFF00E5FF), radius = h * 0.32f, center = Offset(w * (0.15f + 0.6f * shift), h * 0.35f))
        drawCircle(Color(0xFFFFEB3B), radius = h * 0.22f, center = Offset(w * (0.85f - 0.5f * shift), h * 0.7f))
        drawCircle(Color(0xFFFF4081), radius = h * 0.18f, center = Offset(w * 0.5f, h * (0.2f + 0.5f * shift)))
        // Fine stripes make refraction visible.
        var x = 0f
        while (x < w) {
            drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(3f, h))
            x += 22f
        }
    }
}

private val ChipShape = RoundedCornerShape(20.dp)
private val ChipHeight = 64.dp

@Composable
private fun ChipLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(ChipHeight), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlatChip(modifier: Modifier) {
    ChipLabel(
        "Off",
        modifier
            .clip(ChipShape)
            .background(Color.Black.copy(alpha = 0.35f)),
    )
}

private val FrostedStyle = HazeBlurStyle {
    blurRadius(16.dp)
    noiseFactor(0.08f)
    colorEffects(listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.22f))))
    fallbackColorEffect(HazeColorEffect.tint(Color.Black.copy(alpha = 0.55f)))
}

@Composable
private fun FrostedChip(hazeState: HazeState, modifier: Modifier) {
    ChipLabel(
        "Light",
        modifier
            .clip(ChipShape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = FrostedStyle),
    )
}

private val LiquidStyle = GlassStyle.regular.then {
    shape(RoundedCornerShape(20.dp))
    tint(Color.Black.copy(alpha = 0.12f))
}

@Composable
private fun LiquidChip(hazeState: HazeState, modifier: Modifier) {
    ChipLabel(
        "Full",
        modifier.hazeGlass(input = HazeInput.Sources(hazeState), style = LiquidStyle),
    )
}

@Composable
private fun WallpaperCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Probes.WallpaperResult?>(null) }
    Section("Wallpaper access") {
        Text(
            "Can a normal app read the wallpaper image? Needed for real glass over the wallpaper.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { scope.launch { result = Probes.wallpaper(context) } }) { Text("Probe wallpaper") }
        result?.let { r ->
            Text(r.summary, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                r.thumbnail?.let { Image(it, contentDescription = "Wallpaper as seen by the app", Modifier.height(120.dp).clip(RoundedCornerShape(12.dp))) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Wallpaper colors", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        r.colors.forEach { Box(Modifier.size(24.dp).clip(CircleShape).background(it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Probes.IconResult?>(null) }
    Section("Icons and themes") {
        Text(
            "Shows the icons the system gives this launcher. Apply a Theme Park icon pack in " +
                "Theme Park, come back, and probe again to see whether the icons change.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { scope.launch { result = Probes.icons(context) } }) { Text("Probe icons") }
        result?.let { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                r.icons.take(6).forEach { (label, bmp) ->
                    Image(bmp, contentDescription = label, Modifier.size(44.dp))
                }
            }
            Text("Icon packs found: ${if (r.iconPacks.isEmpty()) "none" else r.iconPacks.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            Text("Theme/icon packages installed:", style = MaterialTheme.typography.labelMedium)
            Text(r.themePackages.take(40).joinToString("\n").ifEmpty { "none" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}
