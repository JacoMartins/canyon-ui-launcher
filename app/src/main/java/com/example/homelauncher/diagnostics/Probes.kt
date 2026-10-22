package com.example.homelauncher.diagnostics

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stage 0 device probes. They answer questions that can only be answered on the phone:
 * can a normal app read the wallpaper, and does the system hand third-party launchers
 * themed (Theme Park / Galaxy Themes) icons.
 */
object Probes {

    class WallpaperResult(val summary: String, val thumbnail: ImageBitmap?, val colors: List<Color>)

    class IconResult(
        val icons: List<Pair<String, ImageBitmap>>,
        val iconPacks: List<String>,
        val themePackages: List<String>,
    )

    // Intent actions that icon packs declare for the common launcher conventions.
    private val ICON_PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    /**
     * Tries to read the system wallpaper. Since Android 13 this is restricted to holders of
     * MANAGE_EXTERNAL_STORAGE; without it the platform either throws or returns the default
     * wallpaper. The thumbnail lets the tester see which of the two happened.
     */
    @SuppressLint("MissingPermission") // Deliberate: the probe reports what happens without it.
    suspend fun wallpaper(context: Context): WallpaperResult = withContext(Dispatchers.IO) {
        val wm = WallpaperManager.getInstance(context)
        val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let { wc ->
            listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor)
                .map { Color(it.toArgb()) }
        }.orEmpty()
        val info = wm.wallpaperInfo
        if (info != null) {
            return@withContext WallpaperResult(
                "Live wallpaper (${info.packageName}): no bitmap to read.", null, colors,
            )
        }
        try {
            val d = wm.drawable
            if (d == null) {
                WallpaperResult("getDrawable() returned null.", null, colors)
            } else {
                val w = d.intrinsicWidth
                val h = d.intrinsicHeight
                val thumbH = 240
                val thumbW = if (w > 0 && h > 0) (thumbH * w / h).coerceIn(60, 400) else 135
                WallpaperResult(
                    "getDrawable() OK: ${w}x$h. Compare the thumbnail with your wallpaper.",
                    d.toBitmap(thumbW, thumbH).asImageBitmap(),
                    colors,
                )
            }
        } catch (e: SecurityException) {
            WallpaperResult("SecurityException: ${e.message}", null, colors)
        } catch (e: RuntimeException) {
            WallpaperResult("${e.javaClass.simpleName}: ${e.message}", null, colors)
        }
    }

    suspend fun icons(context: Context): IconResult = withContext(Dispatchers.IO) {
        val la = context.getSystemService(LauncherApps::class.java)
        val density = context.resources.displayMetrics.densityDpi
        val sizePx = (48 * context.resources.displayMetrics.density).toInt()
        val icons = la.getActivityList(null, Process.myUserHandle())
            .sortedBy { it.label.toString().lowercase() }
            .take(10)
            .map { it.label.toString() to it.getIcon(density).toBitmap(sizePx, sizePx).asImageBitmap() }

        val pm = context.packageManager
        val packs = ICON_PACK_ACTIONS.flatMap { action ->
            queryActivities(pm, Intent(action)).map { it.activityInfo.packageName }
        }.distinct().sorted()

        val themePkgs = installedPackageNames(pm)
            .filter { it.contains("theme", ignoreCase = true) || it.contains("icon", ignoreCase = true) }
            .sorted()

        IconResult(icons, packs, themePkgs)
    }

    private fun queryActivities(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun installedPackageNames(pm: PackageManager): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }.map { it.packageName }
}
