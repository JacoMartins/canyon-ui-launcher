package io.github.jacomartins.canyonui.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import java.io.File

/**
 * Which of Canyon UI's own live wallpapers is chosen, the Day cycle's photo, and whether Canyon's
 * wallpaper is the one on screen. The wallpaper service and the launcher run in the same app and
 * read the same choice, so the frosted and glass effects follow the wallpaper exactly.
 */
object CanyonWallpaper {
    private const val PREFS = "canyon_wallpaper"
    const val KEY_SCENE = "scene"
    private const val PHOTO = "canyon_day_cycle.png"

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun scene(context: Context): Scene = Scene.of(prefs(context).getString(KEY_SCENE, null)) ?: Scene.GRADIENT

    fun setScene(context: Context, scene: Scene) = prefs(context).edit { putString(KEY_SCENE, scene.key) }

    fun component(context: Context) = ComponentName(context, CanyonWallpaperService::class.java)

    /** Canyon UI's live wallpaper is the home screen's wallpaper now. */
    fun isActive(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)
    } catch (e: Exception) {
        false
    }

    /** The scene on screen, or null when the wallpaper is someone else's. */
    fun activeScene(context: Context): Scene? = if (isActive(context)) scene(context) else null

    /** Android's own preview, where the user confirms setting Canyon's live wallpaper. */
    fun previewIntent(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))

    fun photoFile(context: Context) = File(context.applicationContext.filesDir, PHOTO)

    /** The Day cycle's photo, already cropped to the screen; null when none was chosen yet. */
    fun photo(context: Context): Bitmap? = try {
        photoFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (e: OutOfMemoryError) {
        null
    }

    fun savePhoto(context: Context, photo: Bitmap) {
        photoFile(context).outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A new photo is a change of the choice too: the wallpaper and the effects redraw it.
        prefs(context).edit { putLong("photo_at", System.currentTimeMillis()) }
    }

    /** Minutes since midnight now, with the seconds as a fraction. */
    fun minuteNow(): Float {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE) + c.get(java.util.Calendar.SECOND) / 60f
    }

    /** Minutes since midnight now. */
    fun minuteOfDay(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
    }
}
