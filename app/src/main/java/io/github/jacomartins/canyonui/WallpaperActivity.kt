package io.github.jacomartins.canyonui

import android.Manifest
import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.wallpaper.WallpaperService
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.jacomartins.canyonui.ui.design.PrimaryButton
import io.github.jacomartins.canyonui.ui.design.SecondaryButton
import io.github.jacomartins.canyonui.ui.design.SettingsSection
import io.github.jacomartins.canyonui.ui.design.Spacing
import io.github.jacomartins.canyonui.ui.design.WallpaperBackdrop
import io.github.jacomartins.canyonui.ui.design.settingsPageColor
import io.github.jacomartins.canyonui.ui.theme.HomeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Canyon UI's own wallpaper chooser (the edit mode's Wallpaper button):
 * - Canyon UI wallpapers: the launcher ships none yet.
 * - Gallery: the photo picker, plus the latest photos when photo access is allowed.
 * - Live wallpapers: installed ones; Android's own preview sets them.
 *
 * A photo set on the home screen from here is also what the frosted and glass effects blur.
 */
class WallpaperActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp
        setContent {
            val settings by app.settings.settings.collectAsState(initial = null)
            HomeTheme(phonePalette = settings?.phonePalette == true) {
                Surface(Modifier.fillMaxSize(), color = settingsPageColor()) {
                    WallpaperScreen(onDone = ::finish)
                }
            }
        }
    }
}

/** A live wallpaper: its service and what to show for it. */
private class LiveWallpaper(val component: ComponentName, val label: String, val thumb: ImageBitmap?)

private val READ_IMAGES = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

private fun canReadPhotos(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, READ_IMAGES) == PackageManager.PERMISSION_GRANTED) return true
    // Android 14+: access to the photos the user picked in the permission prompt.
    return Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun WallpaperScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<Uri?>(null) }
    var canRead by remember { mutableStateOf(canReadPhotos(context)) }
    var asked by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) chosen = uri }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        canRead = canReadPhotos(context)
    }
    val recent by produceState(emptyList<Pair<Uri, ImageBitmap>>(), canRead) {
        value = if (canRead) withContext(Dispatchers.IO) { recentPhotos(context) } else emptyList()
    }
    val live by produceState(emptyList<LiveWallpaper>()) {
        value = withContext(Dispatchers.IO) { liveWallpapers(context) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            stringResource(R.string.choose_wallpaper),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Spacing.mdLg, top = Spacing.lg, bottom = Spacing.md),
        )

        SettingsSection(title = stringResource(R.string.wallpaper_ours)) {
            Text(stringResource(R.string.wallpaper_ours_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = stringResource(R.string.wallpaper_gallery)) {
            PrimaryButton(
                stringResource(R.string.wallpaper_add_gallery),
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = R.drawable.ph_plus,
            )
            if (recent.isNotEmpty()) {
                ThumbGrid(recent.map { it.second }, aspect = 1f) { i -> chosen = recent[i].first }
            } else if (!canRead) {
                if (asked) {
                    Text(stringResource(R.string.wallpaper_recent_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SecondaryButton(stringResource(R.string.wallpaper_show_recent), {
                        val perms = buildList {
                            add(READ_IMAGES)
                            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }
                        permission.launch(perms.toTypedArray())
                    }, icon = R.drawable.ph_wallpaper)
                }
            }
        }

        SettingsSection(title = stringResource(R.string.wallpaper_live)) {
            if (live.isEmpty()) {
                Text(stringResource(R.string.wallpaper_live_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                for (w in live) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { openLive(context, w.component) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) {
                            w.thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Text(w.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.smMd))
                    }
                }
            }
        }
    }

    chosen?.let { uri ->
        WhereDialog(
            onPick = { which ->
                chosen = null
                scope.launch {
                    val ok = WallpaperBackdrop.setFromPhoto(context, uri, which)
                    Toast.makeText(context, if (ok) R.string.wallpaper_done else R.string.render_photo_failed, Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            },
            onDismiss = { chosen = null },
        )
    }
}

/** Three photos a row, rounded, tappable. */
@Composable
private fun ThumbGrid(images: List<ImageBitmap>, aspect: Float, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (row in images.indices.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (i in row) {
                    Image(
                        images[i],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { onPick(i) },
                    )
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Home, lock or both. */
@Composable
private fun WhereDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallpaper_set_where)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val home = stringResource(R.string.wallpaper_home)
                PrimaryButton(home, { onPick(WallpaperManager.FLAG_SYSTEM) }, Modifier.fillMaxWidth().semantics { contentDescription = home })
                SecondaryButton(stringResource(R.string.wallpaper_lock), { onPick(WallpaperManager.FLAG_LOCK) }, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.wallpaper_both), { onPick(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { SecondaryButton(stringResource(R.string.cancel), onDismiss) },
        shape = RoundedCornerShape(28.dp),
    )
}

/** The latest photos (needs photo access), as small thumbnails. */
private fun recentPhotos(context: Context): List<Pair<Uri, ImageBitmap>> {
    val out = ArrayList<Pair<Uri, ImageBitmap>>()
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val args = Bundle().apply {
        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, RECENT_COUNT)
    }
    try {
        resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                val thumb = try {
                    resolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null).asImageBitmap()
                } catch (e: Exception) {
                    continue
                }
                out += uri to thumb
            }
        }
    } catch (e: SecurityException) {
        return emptyList()
    }
    return out
}

/** Installed live wallpapers, by name. */
private fun liveWallpapers(context: Context): List<LiveWallpaper> {
    val pm = context.packageManager
    val services = pm.queryIntentServices(Intent(WallpaperService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
    return services.mapNotNull { ri ->
        try {
            val info = WallpaperInfo(context, ri)
            val thumb = info.loadThumbnail(pm)?.let { d ->
                d.toBitmap(THUMB_PX, (THUMB_PX * d.intrinsicHeight.coerceAtLeast(1) / d.intrinsicWidth.coerceAtLeast(1)).coerceAtLeast(1)).asImageBitmap()
            }
            LiveWallpaper(info.component, info.loadLabel(pm).toString(), thumb)
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.label.lowercase() }
}

/** Android's preview of [component], where it is set. */
private fun openLive(context: Context, component: ComponentName) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.wallpaper_live_unavailable, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, R.string.wallpaper_live_unavailable, Toast.LENGTH_SHORT).show()
    }
}

private const val RECENT_COUNT = 12
private const val THUMB_PX = 256
