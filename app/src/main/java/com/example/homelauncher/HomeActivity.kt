package com.example.homelauncher

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import java.lang.ref.WeakReference
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.data.HomeState
import com.example.homelauncher.model.Container
import com.example.homelauncher.ui.drawer.AppDrawer
import com.example.homelauncher.ui.drawer.DrawerActions
import com.example.homelauncher.ui.drawer.DrawerModel
import com.example.homelauncher.ui.home.AppIconView
import com.example.homelauncher.ui.home.CellLayout
import com.example.homelauncher.ui.home.DragLayer
import com.example.homelauncher.ui.home.DrawerController
import com.example.homelauncher.ui.home.HomeOptionsBar
import com.example.homelauncher.ui.home.HomeScreenLayout
import com.example.homelauncher.ui.theme.HomeTheme
import com.example.homelauncher.util.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The HOME activity. The home surface is View-based (pages, dock, icons); the app drawer is a
 * single ComposeView above it.
 */
class HomeActivity : ComponentActivity(), DrawerController.Listener, DrawerActions {

    private val launcher get() = application as LauncherApp
    private lateinit var launcherApps: LauncherApps

    private lateinit var dragLayer: DragLayer
    private lateinit var home: HomeScreenLayout
    private lateinit var drawerView: ComposeView
    private lateinit var drawer: DrawerController
    private lateinit var drawerModel: DrawerModel
    private lateinit var optionsBar: HomeOptionsBar

    // Return-to-home animation: the icon an app was opened from, and whether we left for it.
    private var launchedFrom: WeakReference<View>? = null
    private var launchPending = false
    private var returningFromApp = false
    private val overshoot = OvershootInterpolator(1.4f)
    private val decelerate = DecelerateInterpolator()

    private val optionsBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = optionsBar.dismiss()
    }

    private val homeStyle = AppIconView.LabelStyle(LIGHT_LABEL, LABEL_SHADOW)
    private val dockStyle = AppIconView.LabelStyle(LIGHT_LABEL, LABEL_SHADOW)
    private val iconViews = HashMap<Long, AppIconView>()
    private var boundOnce = false
    /** The wallpaper's own hint that it is light enough for dark text. */
    private var wallpaperIsLight = false
    private var hintVisible = false
    private var hintSeen = true
    private var drawerEnabled = true

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { drawerModel.query = it }
    }

    override val voiceSearch: (() -> Unit)? by lazy {
        if (packageManager.queryIntentActivities(speechIntent(), 0).isEmpty()) null else ::launchVoiceSearch
    }

    private fun launchVoiceSearch() {
        try {
            speech.launch(speechIntent())
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.voice_search_unavailable))
        }
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

    private val wallpaperManager by lazy { WallpaperManager.getInstance(this) }
    private val colorsListener = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) refreshWallpaperColors()
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackProgressed(backEvent: BackEventCompat) = drawer.previewBack(backEvent.progress)
        override fun handleOnBackCancelled() = drawer.cancelBackPreview()
        override fun handleOnBackPressed() = drawer.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars: the default style adds a contrast scrim behind the gesture bar
        // on some devices (a grey strip under the dock and drawer).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        launcherApps = getSystemService(LauncherApps::class.java)

        home = HomeScreenLayout(this, 5, 6, 4).apply { alpha = 1f }
        // Content fades in once on the first bind so a cold start never shows a half-built page.
        home.workspace.alpha = 0f
        home.hotseat.alpha = 0f
        home.indicator.alpha = 0f
        home.workspace.onScroll = home.indicator::setPosition
        home.onEmptyLongPress = ::showHomeOptions

        drawerModel = DrawerModel(launcher.apps, launcher.settings, lifecycleScope)

        dragLayer = DragLayer(this)
        drawerView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.INVISIBLE // Composed and laid out up front so the first open is smooth.
            setContent {
                HomeTheme { AppDrawer(drawerModel, dragLayer.drawerTouch, this@HomeActivity) }
            }
        }
        dragLayer.addView(home, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        dragLayer.addView(drawerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        optionsBar = HomeOptionsBar(this, ::chooseWallpaper, ::openSettings, ::onHomeOptionsDismissed)
        dragLayer.addView(optionsBar, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        drawer = DrawerController(home, drawerView, this)
        dragLayer.drawer = drawer
        setContentView(dragLayer)
        // TalkBack and switch access users get the drawer without the swipe.
        ViewCompat.addAccessibilityAction(home, getString(R.string.action_open_all_apps)) { _, _ -> drawerEnabled.also { if (it) drawer.open() } }
        // Start the drawer below the screen before the first frame.
        dragLayer.post { drawer.close(animate = false) }

        // A launcher is the bottom of the back stack: Back on home does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        onBackPressedDispatcher.addCallback(this, backCallback)
        onBackPressedDispatcher.addCallback(this, optionsBack)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcher.home.state.filterNotNull().collect(::bind)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcher.settings.settings.collect { s ->
                    hintSeen = s.drawerHintSeen
                    setHintVisible(!hintSeen && drawerEnabled)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        wallpaperManager.addOnColorsChangedListener(colorsListener, Handler(Looper.getMainLooper()))
        refreshWallpaperColors()
    }

    override fun onResume() {
        super.onResume()
        if (returningFromApp) {
            returningFromApp = false
            playReturnAnimation(launchedFrom?.get())
            launchedFrom = null
        }
    }

    override fun onStop() {
        if (launchPending) returningFromApp = true
        launchPending = false
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed. If home was already showing, animate back to the first page or close the
        // drawer; if returning from another app, reset without animation.
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (optionsBar.isShowing) {
                optionsBar.dismiss()
                return
            }
            if (!drawer.isClosed) {
                drawer.close(animate = visible)
            } else {
                home.workspace.snapToPage(0, animate = visible)
            }
        }
    }

    // --- Binding -----------------------------------------------------------------------------

    private fun bind(state: HomeState) {
        var restyled = false
        for ((style, show) in listOf(homeStyle to state.showHomeLabels, dockStyle to state.showDockLabels)) {
            val size = state.iconSize
            if (style.iconCellFraction != size.cellFraction || style.maxIconDp != size.maxDp || style.showLabel != show) {
                style.iconCellFraction = size.cellFraction
                style.maxIconDp = size.maxDp
                style.showLabel = show
                restyled = true
            }
        }
        if (restyled) iconViews.values.forEach { it.invalidate() }
        if (drawerEnabled != state.drawerEnabled) {
            drawerEnabled = state.drawerEnabled
            dragLayer.drawerEnabled = drawerEnabled
            if (!drawerEnabled && !drawer.isClosed) drawer.close(animate = false)
            setHintVisible(!hintSeen && drawerEnabled)
        }
        home.setGrid(state.columns, state.rows, state.dockSlots)
        home.setPageCount(state.pageCount)

        val seen = HashSet<Long>(state.items.size)
        for (item in state.items) {
            seen += item.id
            val p = item.placed
            val view = iconViews.getOrPut(item.id) { newIconView() }
            view.bind(item.app)
            val parent: CellLayout = if (p.container == Container.DOCK) home.hotseat else home.workspace.pageAt(p.pageIndex) ?: continue
            view.useStyle(if (p.container == Container.DOCK) dockStyle else homeStyle)
            val lp = view.layoutParams as? CellLayout.LayoutParams
            if (view.parent !== parent) {
                (view.parent as? CellLayout)?.removeView(view)
                parent.addView(view, CellLayout.LayoutParams(p.x, p.y, p.spanX, p.spanY))
            } else if (lp != null && (lp.cellX != p.x || lp.cellY != p.y || lp.spanX != p.spanX || lp.spanY != p.spanY)) {
                lp.cellX = p.x; lp.cellY = p.y; lp.spanX = p.spanX; lp.spanY = p.spanY
                view.requestLayout()
            }
        }
        val it = iconViews.entries.iterator()
        while (it.hasNext()) {
            val (id, view) = it.next()
            if (id !in seen) {
                (view.parent as? CellLayout)?.removeView(view)
                it.remove()
            }
        }

        if (!boundOnce) {
            boundOnce = true
            for (v in listOf(home.workspace, home.hotseat, home.indicator)) {
                if (Motion.enabled()) v.animate().alpha(1f).setDuration(FADE_IN_MS).start() else v.alpha = 1f
            }
        }
    }

    private fun newIconView() = AppIconView(this, homeStyle).apply {
        setOnClickListener { v -> (v as AppIconView).app?.let { launchFromView(v, it) } }
        setOnLongClickListener { v ->
            val app = (v as AppIconView).app ?: return@setOnLongClickListener false
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showIconMenu(v, app)
            true
        }
    }

    /** Stage 1 long-press menu. Stage 2 replaces it with drag and drop and the full menu. */
    private fun showIconMenu(anchor: View, app: AppInfo) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.menu_app_info).setOnMenuItemClickListener { appInfo(app, anchor.screenBounds()); true }
            if (!drawerEnabled) {
                // Home-screen-only mode has no drawer, so hiding happens here; Settings lists hidden apps.
                menu.add(R.string.menu_hide).setOnMenuItemClickListener { drawerModel.setHidden(app, true); true }
            }
            if (!app.isSystem) menu.add(R.string.menu_uninstall).setOnMenuItemClickListener { uninstall(app); true }
            show()
        }
    }

    // --- Wallpaper-aware colours ---------------------------------------------------------------

    private fun refreshWallpaperColors() {
        lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) { wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }
            wallpaperIsLight = Build.VERSION.SDK_INT >= 31 && colors != null &&
                colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            applyLabelColors()
            updateSystemBars(drawer.progress > 0.5f)
        }
    }

    private val isNight: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Dark labels only for a light wallpaper in light mode. Dark mode always gets light labels:
     * One UI can dim the wallpaper in dark mode without changing the wallpaper's colour hints,
     * so trusting the hint there gives black text on a darkened wallpaper.
     */
    private val darkLabels: Boolean get() = wallpaperIsLight && !isNight

    private fun applyLabelColors() {
        val dark = darkLabels
        for (style in listOf(homeStyle, dockStyle)) {
            style.color = if (dark) DARK_LABEL else LIGHT_LABEL
            style.shadowColor = if (dark) 0 else LABEL_SHADOW
        }
        iconViews.values.forEach { it.applyStyle() }
        if (isNight) optionsBar.setColors(0xF0202124.toInt(), 0xFFFFFFFF.toInt())
        else optionsBar.setColors(0xF5FFFFFF.toInt(), 0xFF1C1B1F.toInt())
        if (dark) {
            home.setSurfaceColors(0x1F000000, 0xE6FFFFFF.toInt(), 0xFF1C1B1F.toInt(), DARK_LABEL)
        } else {
            home.setSurfaceColors(0x26FFFFFF, 0xE6202124.toInt(), 0xFFFFFFFF.toInt(), LIGHT_LABEL)
        }
    }

    private fun updateSystemBars(drawerMostlyOpen: Boolean) {
        // Light (dark-icon) bars over the light drawer, or over a light wallpaper in light mode.
        val lightBars = if (drawerMostlyOpen) !isNight else darkLabels
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode is handled here (no restart), so dark/light switches must recolour by hand.
        applyLabelColors()
        updateSystemBars(drawer.progress > 0.5f)
    }

    // --- Drawer callbacks --------------------------------------------------------------------

    override fun onDrawerOpened() {
        backCallback.isEnabled = true
        drawerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (hintVisible) lifecycleScope.launch { launcher.settings.setDrawerHintSeen() }
    }

    override fun onDrawerClosed() {
        backCallback.isEnabled = false
        drawerModel.query = ""
        drawerView.clearFocus()
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        drawerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    override fun onDrawerMostlyOpen(mostlyOpen: Boolean) = updateSystemBars(mostlyOpen)

    private fun setHintVisible(visible: Boolean) {
        if (visible == hintVisible) return
        hintVisible = visible
        val hint = home.hint
        hint.setText(R.string.hint_swipe_up)
        if (visible) {
            hint.visibility = View.VISIBLE
            hint.animate().alpha(1f).setStartDelay(600).setDuration(250).start()
        } else {
            hint.animate().alpha(0f).setStartDelay(0).setDuration(150).withEndAction { hint.visibility = View.GONE }.start()
        }
    }

    // --- Actions -----------------------------------------------------------------------------

    // --- Home options (long-press on an empty spot) -----------------------------------------------

    private fun showHomeOptions() {
        if (!drawer.isClosed || optionsBar.isShowing) return
        dragLayer.drawerEnabled = false
        optionsBack.isEnabled = true
        if (hintVisible) home.hint.animate().alpha(0f).setDuration(120).start()
        zoomHome(OPTIONS_SCALE)
        optionsBar.show()
    }

    private fun onHomeOptionsDismissed() {
        dragLayer.drawerEnabled = drawerEnabled
        optionsBack.isEnabled = false
        if (hintVisible) home.hint.animate().alpha(1f).setDuration(150).start()
        zoomHome(1f)
    }

    private fun zoomHome(scale: Float) {
        for (v in listOf(home.workspace, home.indicator, home.hotseat)) {
            v.pivotY = home.height / 2f - v.top
            if (Motion.enabled()) {
                v.animate().scaleX(scale).scaleY(scale).setDuration(220).setInterpolator(decelerate).start()
            } else {
                v.scaleX = scale
                v.scaleY = scale
            }
        }
    }

    private fun chooseWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.wallpaper_unavailable))
        }
    }

    // --- Return-to-home animation -----------------------------------------------------------

    /**
     * The system draws the closing app (a third-party launcher cannot take over that part), so
     * the home screen meets it: pages settle in from a slight zoom and the icon the app was
     * opened from grows back into place. Skipped if that icon is gone.
     */
    private fun playReturnAnimation(icon: View?) {
        if (!Motion.enabled() || !drawer.isClosed || optionsBar.isShowing) return
        for (v in listOf(home.workspace, home.indicator, home.hotseat)) {
            v.pivotY = home.height / 2f - v.top
            v.scaleX = RETURN_SCALE
            v.scaleY = RETURN_SCALE
            v.alpha = 0.6f
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280).setInterpolator(decelerate).start()
        }
        if (icon != null && icon.isAttachedToWindow && icon.parent != null) {
            icon.scaleX = 0.6f
            icon.scaleY = 0.6f
            icon.animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(overshoot).start()
        }
    }

    private fun launchFromView(v: View, app: AppInfo) {
        launchedFrom = WeakReference(v)
        val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle()
        start(app, v.screenBounds(), opts)
    }

    override fun launch(app: AppInfo, bounds: Rect?) {
        launchedFrom = null
        val opts = bounds?.let {
            ActivityOptions.makeScaleUpAnimation(drawerView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        start(app, bounds, opts)
    }

    private fun start(app: AppInfo, bounds: Rect?, opts: Bundle?) {
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, opts)
            launchPending = true
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.app_unavailable, app.label))
        } catch (e: SecurityException) {
            toast(getString(R.string.app_cannot_open, app.label))
        }
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun appInfo(app: AppInfo, bounds: Rect?) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, bounds, null)
        } catch (e: RuntimeException) {
            toast(getString(R.string.app_info_unavailable))
        }
    }

    override fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.key.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.cannot_uninstall, app.label))
        }
    }

    override fun addToHome(app: AppInfo) {
        lifecycleScope.launch {
            val page = launcher.home.addToHome(app, home.workspace.currentPage)
            if (page >= 0) toast(getString(R.string.added_to_page, app.label, page + 1))
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun View.screenBounds(): Rect {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
    }

    companion object {
        private const val LIGHT_LABEL = 0xFFFFFFFF.toInt()
        private const val DARK_LABEL = 0xFF1C1B1F.toInt()
        private const val LABEL_SHADOW = 0x8A000000.toInt()
        private const val FADE_IN_MS = 180L
        private const val OPTIONS_SCALE = 0.9f
        private const val RETURN_SCALE = 1.06f
    }
}
