package com.example.homelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.example.homelauncher.model.AppKey
import com.example.homelauncher.model.AppSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A launchable activity with its label and rendered icon. */
@Immutable
class AppInfo(
    val key: AppKey,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: Bitmap,
    val isSystem: Boolean,
) {
    /** Compose wrapper around [icon]; created once, no copy. */
    val image: ImageBitmap = icon.asImageBitmap()
    val isMainProfile: Boolean get() = user == Process.myUserHandle()
}

/**
 * Rendered app icons, keyed by [AppKey]. Bounded by bytes; entries for a package are dropped
 * when the package changes so updated icons are picked up. When an icon [pack] is set, its
 * drawable is used for every app it covers; other apps keep the system icon.
 */
class IconCache(context: Context) {
    private val resources = context.resources
    val iconSizePx: Int = (ICON_DP * context.resources.displayMetrics.density).toInt()
    private val density = context.resources.displayMetrics.densityDpi

    private val cache = object : LruCache<AppKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: AppKey, value: Bitmap) = value.allocationByteCount
    }

    @Volatile
    var pack: IconPack? = null
        private set

    /** Switches packs and drops every rendered icon. */
    fun setPack(newPack: IconPack?) {
        pack = newPack
        cache.evictAll()
    }

    /** Calendar icons from the pack change with the date. */
    fun invalidateCalendars() {
        val p = pack ?: return
        cache.snapshot().keys.filter { p.isCalendar(it.packageName, it.className) }.forEach { cache.remove(it) }
    }

    fun get(info: LauncherActivityInfo, key: AppKey, pm: android.content.pm.PackageManager): Bitmap =
        cache.get(key) ?: render(info, key, pm).also { cache.put(key, it) }

    private fun render(info: LauncherActivityInfo, key: AppKey, pm: android.content.pm.PackageManager): Bitmap {
        // Pack artwork comes straight from the pack's resources, so it carries none of the effects
        // a device's theme engine bakes into the icons it hands to launchers.
        val raw = pack?.drawableFor(key.packageName, key.className, density) ?: info.getIcon(density)
        val bmp = if (raw is AdaptiveIconDrawable) renderAdaptive(raw) else raw.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
        if (info.user == Process.myUserHandle()) return bmp
        val badged = pm.getUserBadgedIcon(bmp.toDrawable(resources), info.user)
        return badged.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
    }

    /**
     * Builds the icon from an adaptive icon's own layers and mask instead of drawing the drawable
     * as a whole, so effects a device wraps around the drawable (such as a drop shadow) are left
     * out. Layers are 1.5x the visible size and centred, per the adaptive icon spec.
     */
    private fun renderAdaptive(d: AdaptiveIconDrawable): Bitmap {
        val size = iconSizePx
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        canvas.clipPath(d.iconMask)
        val inset = size / 4
        for (layer in listOf(d.background, d.foreground)) {
            layer ?: continue
            layer.setBounds(-inset, -inset, size + inset, size + inset)
            layer.draw(canvas)
        }
        return bmp
    }

    fun invalidatePackage(packageName: String) {
        cache.snapshot().keys.filter { it.packageName == packageName }.forEach { cache.remove(it) }
    }

    companion object {
        /** Largest on-screen size (IconSize.LARGE cap), so icons are only ever scaled down. */
        const val ICON_DP = 80
        private const val MAX_BYTES = 24 * 1024 * 1024
    }
}

/**
 * Every launchable activity in every profile, kept current through [LauncherApps.Callback].
 * Loading and icon rendering run on [Dispatchers.Default]; bursts of package events are merged.
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    val icons: IconCache,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)

    /** Sorted A-Z. Null until the first load finishes. */
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val reloads = Channel<Unit>(Channel.CONFLATED)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
    }

    private fun changed(packageName: String) {
        icons.invalidatePackage(packageName)
        // The selected pack was updated, removed or re-enabled: re-read it.
        if (packageName == packPackage) setIconPack(packPackage) else reloads.trySend(Unit)
    }

    private var packPackage: String? = null
    private val packReady = CompletableDeferred<Unit>()

    /**
     * Loads [packageName]'s icon pack (null = system icons) off the main thread and re-renders.
     * The first call also releases the initial app load, so icons never flash from system to pack.
     */
    fun setIconPack(packageName: String?) {
        packPackage = packageName
        scope.launch {
            val pack = withContext(Dispatchers.Default) { packageName?.let { IconPack.load(context, it) } }
            if (packPackage != packageName) return@launch // A newer choice won.
            icons.setPack(pack)
            packReady.complete(Unit)
            reloads.trySend(Unit)
        }
    }

    /** Midnight or a clock change: pack calendar icons show the new day. */
    fun dateChanged() {
        icons.invalidateCalendars()
        reloads.trySend(Unit)
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch {
            packReady.await()
            for (unused in reloads) {
                _apps.value = withContext(Dispatchers.Default) { load() }
                delay(RELOAD_COALESCE_MS)
            }
        }
    }

    /** Labels depend on the locale. */
    fun reload() {
        reloads.trySend(Unit)
    }

    private fun load(): List<AppInfo> {
        val pm = context.packageManager
        val out = ArrayList<AppInfo>()
        for (profile in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(profile)
            // A locked or paused profile (work profile, Secure Folder) can refuse the query.
            val activities = try {
                launcherApps.getActivityList(null, profile)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (info in activities) {
                val cn = info.componentName
                val key = AppKey(cn.packageName, cn.className, serial)
                val flags = info.applicationInfo.flags
                out += AppInfo(
                    key = key,
                    component = cn,
                    user = profile,
                    label = info.label.toString().trim().ifEmpty { cn.packageName },
                    icon = icons.get(info, key, pm),
                    isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0,
                )
            }
        }
        return AppSearch.sortByLabel(out) { it.label }
    }

    companion object {
        private const val RELOAD_COALESCE_MS = 250L
    }
}
