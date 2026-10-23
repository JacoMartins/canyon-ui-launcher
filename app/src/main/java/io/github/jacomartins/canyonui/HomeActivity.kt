package io.github.jacomartins.canyonui

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import io.github.jacomartins.canyonui.data.titleRes
import io.github.jacomartins.canyonui.data.HomeRepository
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jacomartins.canyonui.data.AppEntry
import io.github.jacomartins.canyonui.data.AppInfo
import io.github.jacomartins.canyonui.data.DragItem
import io.github.jacomartins.canyonui.data.FolderEntry
import io.github.jacomartins.canyonui.data.HomeEntry
import io.github.jacomartins.canyonui.data.HomeState
import io.github.jacomartins.canyonui.data.IconFiles
import io.github.jacomartins.canyonui.data.IconOverride
import io.github.jacomartins.canyonui.data.IconPack
import io.github.jacomartins.canyonui.data.LayoutMode
import io.github.jacomartins.canyonui.data.StackEntry
import io.github.jacomartins.canyonui.data.WidgetEntry
import io.github.jacomartins.canyonui.model.GridOccupancy
import io.github.jacomartins.canyonui.model.LayoutResolver
import io.github.jacomartins.canyonui.model.AppCategories
import io.github.jacomartins.canyonui.model.Container
import io.github.jacomartins.canyonui.model.DropAction
import io.github.jacomartins.canyonui.model.ItemType
import io.github.jacomartins.canyonui.ui.drawer.AppDrawer
import io.github.jacomartins.canyonui.ui.drawer.DrawerActions
import io.github.jacomartins.canyonui.ui.drawer.DrawerModel
import io.github.jacomartins.canyonui.ui.home.AppIconView
import io.github.jacomartins.canyonui.ui.home.CellLayout
import io.github.jacomartins.canyonui.ui.home.DragController
import io.github.jacomartins.canyonui.ui.home.DragLayer
import io.github.jacomartins.canyonui.ui.home.DragSource
import io.github.jacomartins.canyonui.ui.home.DrawerController
import io.github.jacomartins.canyonui.ui.home.DropResult
import io.github.jacomartins.canyonui.ui.home.FolderIconView
import io.github.jacomartins.canyonui.ui.home.HomeScreenLayout
import io.github.jacomartins.canyonui.ui.home.ViewMap
import io.github.jacomartins.canyonui.ui.overlay.EditAppState
import io.github.jacomartins.canyonui.ui.overlay.HomeOverlay
import io.github.jacomartins.canyonui.ui.overlay.ItemMenu
import io.github.jacomartins.canyonui.ui.design.MenuEntry
import io.github.jacomartins.canyonui.ui.overlay.OverlayActions
import io.github.jacomartins.canyonui.ui.overlay.OverlayState
import io.github.jacomartins.canyonui.ui.overlay.PageThumb
import io.github.jacomartins.canyonui.ui.overlay.Snack
import io.github.jacomartins.canyonui.ui.overlay.WidgetPickerState
import io.github.jacomartins.canyonui.ui.theme.HomeTheme
import io.github.jacomartins.canyonui.ui.widget.LauncherWidgetHostView
import io.github.jacomartins.canyonui.ui.widget.PickerWidget
import io.github.jacomartins.canyonui.ui.widget.ResizeFrame
import io.github.jacomartins.canyonui.ui.widget.WidgetCatalog
import io.github.jacomartins.canyonui.ui.widget.WidgetFrame
import io.github.jacomartins.canyonui.ui.widget.WidgetPlaceholderView
import io.github.jacomartins.canyonui.ui.widget.WidgetSizes
import io.github.jacomartins.canyonui.ui.widget.WidgetStackView
import io.github.jacomartins.canyonui.util.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * The HOME activity. The home surface is View-based (pages, dock, icons, folders, drag and
 * drop); the app drawer and the overlay (menus, folder popup, edit mode, page overview, undo)
 * are one ComposeView each.
 */
class HomeActivity : ComponentActivity(), DrawerController.Listener, DrawerActions, OverlayActions,
    DragController.Callbacks, FolderIconView.Listener {

    private val launcher get() = application as LauncherApp
    private lateinit var launcherApps: LauncherApps

    private lateinit var dragLayer: DragLayer
    private lateinit var home: HomeScreenLayout
    private lateinit var drawerView: ComposeView
    private lateinit var overlayView: ComposeView
    private lateinit var drawer: DrawerController
    private lateinit var drawerModel: DrawerModel
    private lateinit var drag: DragController
    private val ui = OverlayState()

    // Return-to-home animation: the icon an app was opened from, and whether we left for it.
    private var launchedFrom: WeakReference<View>? = null
    private var launchPending = false
    private var returningFromApp = false
    private val overshoot = OvershootInterpolator(1.4f)
    private val decelerate = DecelerateInterpolator()

    private val homeStyle = AppIconView.LabelStyle(LIGHT_LABEL, LABEL_SHADOW)
    private val dockStyle = AppIconView.LabelStyle(LIGHT_LABEL, LABEL_SHADOW)
    private val views = HashMap<Long, View>()
    private var state: HomeState? = null
    private var boundOnce = false
    /** The wallpaper's own hint that it is light enough for dark text. */
    private var wallpaperIsLight = false
    private var hintVisible = false
    private var hintSeen = true
    private var drawerEnabled = true
    /** An empty page shown at the end while dragging, so items can go onto a new page. */
    private var extraPage = false

    private lateinit var widgets: HomeWidgets
    private var resizeFrame: ResizeFrame? = null
    private var resizeAnyWidget = false
    private var swipeUpTarget: String? = null
    private var lastNight: Boolean? = null
    private var iconShape = io.github.jacomartins.canyonui.data.IconShape.SYSTEM
    private var wallpaperTarget: String? = null
    /** Compose state: both ComposeViews recolour when the setting changes. */
    private var phonePalette by mutableStateOf(false)
    private var widgetBorder = true
    private var noOutline: Set<String> = emptySet()

    /** The ring is on unless turned off in settings, or for this kind of widget. */
    private fun outlineFor(entry: HomeEntry): Boolean = widgetBorder && when (entry) {
        is WidgetEntry -> entry.provider.flattenToString() !in noOutline
        is StackEntry -> entry.widgets.none { it.provider.flattenToString() in noOutline }
        else -> true
    }
    private val widgetInset by lazy { (WidgetFrame.INSET_DP * resources.displayMetrics.density).toInt() }
    private val pillMax by lazy { (WidgetFrame.PILL_MAX_DP * resources.displayMetrics.density).toInt() }
    /** Where the picker adds: null for the current page, else onto a widget or stack. */
    private var pickerTarget: DropAction? = null
    private var pickerSpan: Pair<Int, Int>? = null
    /** The last loaded picker contents, to find what a dragged-in widget is. */
    private var pickerWidgets: Map<Pair<android.content.ComponentName, Long>, PickerWidget> = emptyMap()

    private val tmpRect = Rect()
    private val tmpRectF = RectF()
    private val pt = FloatArray(2)

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val edit = ui.editApp
        if (uri == null || edit == null) return@registerForActivityResult
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { IconFiles.save(this@HomeActivity, uri) }
            if (name == null) {
                toast(getString(R.string.edit_app_image_failed))
                return@launch
            }
            // A previous pick in this edit that was not saved is no longer needed.
            discardUnsavedImage(edit)
            edit.draft = edit.draft.copy(file = name, packPackage = null, drawable = null)
        }
    }

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

    private val drawerBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackProgressed(backEvent: BackEventCompat) = drawer.previewBack(backEvent.progress)
        override fun handleOnBackCancelled() = drawer.cancelBackPreview()
        override fun handleOnBackPressed() = drawer.close()
    }

    /** Closes, in order: menu, folder, page overview, edit mode. */
    private val overlayBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                ui.menu != null -> ui.menu = null
                resizeFrame != null -> dismissResizeFrame()
                ui.editApp != null -> closeEditApp()
                ui.picker != null -> closePicker()
                ui.folder != null -> closeFolder()
                ui.overview != null -> closeOverview()
                ui.editMode -> exitEditMode()
            }
            updateBackCallbacks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars: the default style adds a contrast scrim behind the gesture bar
        // on some devices (a grey strip under the dock and drawer).
        applyEdgeToEdge()
        super.onCreate(savedInstanceState)
        clearBarScrims()
        launcherApps = getSystemService(LauncherApps::class.java)
        lastNight = isNight
        widgets = HomeWidgets(
            this,
            onProvidersChanged = ::onWidgetProvidersChanged,
            onAdded = ::onWidgetAdded,
            onFailed = { toast(getString(R.string.widget_add_failed)) },
        )
        widgets.restore(savedInstanceState)

        home = HomeScreenLayout(this, 4, 6, 4)
        // Content fades in once on the first bind so a cold start never shows a half-built page.
        home.workspace.alpha = 0f
        home.hotseat.alpha = 0f
        home.indicator.alpha = 0f
        home.workspace.onScroll = home.indicator::setPosition
        home.workspace.onPageSettled = { ui.currentPage = it }
        home.onEmptyLongPress = { if (ui.editMode) armPageDrag() else enterEditMode() }
        home.onEmptyTap = { if (ui.editMode) exitEditMode() }
        home.onAddPage = ::addPage

        drawerModel = DrawerModel(launcher.apps, launcher.settings, lifecycleScope)

        dragLayer = DragLayer(this)
        drawerView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.INVISIBLE // Composed and laid out up front so the first open is smooth.
            setContent {
                HomeTheme(phonePalette = phonePalette) { AppDrawer(drawerModel, dragLayer.drawerTouch, this@HomeActivity) }
            }
        }
        overlayView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { HomeTheme(phonePalette = phonePalette) { HomeOverlay(ui, this@HomeActivity) } }
        }
        dragLayer.addView(home, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        dragLayer.addView(drawerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        dragLayer.addView(overlayView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        drawer = DrawerController(home, drawerView, this)
        dragLayer.drawer = drawer
        drag = DragController(dragLayer, home, this)
        dragLayer.drag = drag
        dragLayer.onPinchIn = { if (!ui.isModal) enterEditMode() }
        dragLayer.onSwipeUp = { if (!ui.editMode && !ui.isModal) openSwipeTarget() }
        dragLayer.onPinchOut = { if (ui.editMode && !ui.isModal) exitEditMode() }
        setContentView(dragLayer)
        // TalkBack and switch access users get the drawer without the swipe.
        ViewCompat.addAccessibilityAction(home, getString(R.string.action_open_all_apps)) { _, _ -> drawerEnabled.also { if (it) drawer.open() } }
        // Start the drawer below the screen before the first frame.
        dragLayer.post { drawer.close(animate = false) }

        // A launcher is the bottom of the back stack: Back on home does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        onBackPressedDispatcher.addCallback(this, drawerBack)
        onBackPressedDispatcher.addCallback(this, overlayBack)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcher.home.state.filterNotNull().collect(::bind)
            }
        }
        // Dynamic clock icons were redrawn in place: repaint the icons on home.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcher.apps.clockTicks.collect {
                    views.values.forEach { v -> if (v is AppIconView && v.app?.isClock == true || v is FolderIconView) v.invalidate() }
                }
            }
        }
        // Ids of widgets removed earlier (kept until now so Undo could bring them back).
        if (savedInstanceState == null) lifecycleScope.launch { widgets.releaseUnused(launcher.home.widgetIdsInUse()) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcher.settings.settings.collect { s ->
                    hintSeen = s.drawerHintSeen
                    resizeAnyWidget = s.resizeAnyWidget
                    phonePalette = s.phonePalette
                    if (iconShape != s.iconStyle.shape) {
                        iconShape = s.iconStyle.shape
                        views.values.forEach { (it as? FolderIconView)?.let(::styleFolder) }
                    }
                    swipeUpTarget = s.swipeUpTarget
                    wallpaperTarget = s.wallpaperTarget
                    ui.categoryMode = s.autoCategories && s.layoutMode == LayoutMode.HOME_ONLY
                    if (s.pendingSearchWidget) addSearchWidget()
                    if (widgetBorder != s.widgetBorder || noOutline != s.noOutlineWidgets) {
                        widgetBorder = s.widgetBorder
                        noOutline = s.noOutlineWidgets
                        state?.let(::bind)
                    }
                    setHintVisible(!hintSeen && drawerEnabled)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.host.startListeningSafely()
        // Apps may have been removed or reinstalled while home was not listening.
        if (state != null) onWidgetProvidersChanged()
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
        drag.cancel()
        dismissResizeFrame()
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        widgets.host.stopListeningSafely()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        widgets.save(outState)
    }

    @Deprecated("Widget binding and setup screens need request codes.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        if (!widgets.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed. If home was already showing, close what is open, animate back to the
        // first page or close the drawer; if returning from another app, reset without animation.
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            dismissResizeFrame()
            if (ui.isModal || ui.editMode) {
                ui.menu = null
                closeEditApp()
                closePicker()
                closeFolder()
                closeOverview()
                if (ui.editMode) exitEditMode()
                updateBackCallbacks()
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

    private fun bind(st: HomeState) {
        state = st
        var restyled = false
        for ((style, show) in listOf(homeStyle to st.showHomeLabels, dockStyle to st.showDockLabels)) {
            val size = st.iconSize
            if (style.iconCellFraction != size.cellFraction || style.maxIconDp != size.maxDp || style.showLabel != show) {
                style.iconCellFraction = size.cellFraction
                style.maxIconDp = size.maxDp
                style.showLabel = show
                restyled = true
            }
        }
        if (restyled) views.values.forEach { (it as? FolderIconView)?.applyStyle(); it.invalidate() }
        if (drawerEnabled != st.drawerEnabled) {
            drawerEnabled = st.drawerEnabled
            dragLayer.drawerEnabled = drawerEnabled && !ui.editMode
            if (!drawerEnabled && !drawer.isClosed) drawer.close(animate = false)
            setHintVisible(!hintSeen && drawerEnabled)
        }
        home.setGrid(st.columns, st.rows, st.dockSlots)
        syncPages()
        ui.pageCount = st.pageCount
        ui.currentPage = home.workspace.currentPage

        val dragged = drag.source?.view
        val seen = HashSet<Long>(st.entries.size)
        val widgetIds = HashSet<Long>()
        for (entry in st.entries) {
            seen += entry.id
            val p = entry.placed
            val view = viewFor(entry)
            when (entry) {
                is WidgetEntry -> widgetIds += entry.id
                is StackEntry -> {
                    entry.widgets.forEach { widgetIds += it.id }
                    // The stack carries the frame; its widgets are plain inside it.
                    (view as WidgetStackView).setPages(entry.widgets.map { widgetView(it).also(WidgetFrame::clear) })
                }
                else -> Unit
            }
            val parent: CellLayout = if (p.container == Container.DOCK) home.hotseat else home.workspace.pageAt(p.pageIndex) ?: continue
            val style = if (p.container == Container.DOCK) dockStyle else homeStyle
            when (view) {
                is AppIconView -> { view.bind((entry as AppEntry).app); view.useStyle(style) }
                is FolderIconView -> { view.bind(entry as FolderEntry); view.useStyle(style) }
            }
            val isWidget = entry is WidgetEntry || entry is StackEntry
            val inset = if (isWidget) widgetInset else 0
            val thickness = if (isWidget) pillMax else 0
            if (isWidget) WidgetFrame.apply(view, outlineFor(entry), pill = p.spanX == 1 || p.spanY == 1)
            val lp = view.layoutParams as? CellLayout.LayoutParams
            if (view.parent !== parent) {
                (view.parent as? android.view.ViewGroup)?.removeView(view) // A page, the dock or a stack.
                parent.addView(view, CellLayout.LayoutParams(p.x, p.y, p.spanX, p.spanY, inset, thickness))
            } else if (lp != null && (lp.cellX != p.x || lp.cellY != p.y || lp.spanX != p.spanX || lp.spanY != p.spanY || lp.inset != inset || lp.maxThickness != thickness)) {
                lp.cellX = p.x; lp.cellY = p.y; lp.spanX = p.spanX; lp.spanY = p.spanY; lp.inset = inset; lp.maxThickness = thickness
                view.requestLayout()
            }
            view.visibility = if (view === dragged) View.INVISIBLE else View.VISIBLE
        }
        val it = views.entries.iterator()
        while (it.hasNext()) {
            val (id, view) = it.next()
            if (id !in seen) {
                (view.parent as? CellLayout)?.removeView(view)
                it.remove()
            }
        }
        widgets.prune(widgetIds)
        applyWidgetSizes(st)

        // Open folders show their apps at the home screen's icon size (known once pages are laid out).
        dragLayer.post {
            val page = home.workspace.pageAt(0) ?: return@post
            if (page.cellWidth <= 0) return@post
            val d = resources.displayMetrics.density
            ui.homeIconDp = minOf(page.cellWidth * homeStyle.iconCellFraction, homeStyle.maxIconDp * d) / d
        }

        // Keep an open folder in step with changes (renamed, apps added or dragged out).
        ui.folder?.let { open -> ui.folder = st.entry(open.id) as? FolderEntry }
        if (ui.overview != null) dragLayer.post { if (ui.overview != null) ui.overview = captureThumbs() }

        if (!boundOnce) {
            boundOnce = true
            for (v in listOf(home.workspace, home.hotseat, home.indicator)) {
                if (Motion.enabled()) v.animate().alpha(1f).setDuration(FADE_IN_MS).start() else v.alpha = 1f
            }
        }
    }

    private fun viewFor(entry: HomeEntry): View {
        val existing = views[entry.id]
        if (entry is WidgetEntry) {
            val v = widgetView(entry)
            if (existing !== v) (existing?.parent as? CellLayout)?.removeView(existing)
            views[entry.id] = v
            return v
        }
        val fits = when (entry) {
            is FolderEntry -> existing is FolderIconView
            is StackEntry -> existing is WidgetStackView
            else -> existing is AppIconView
        }
        if (existing != null && fits) return existing
        (existing?.parent as? CellLayout)?.removeView(existing)
        val v = when (entry) {
            is FolderEntry -> newFolderView()
            is StackEntry -> WidgetStackView(this)
            else -> newIconView()
        }
        views[entry.id] = v
        return v
    }

    private fun widgetView(entry: WidgetEntry): View = widgets.viewFor(entry) { v ->
        v.setOnLongClickListener { onItemLongPress(it) }
        if (v is WidgetPlaceholderView) {
            v.setOnClickListener { if (ui.editMode) exitEditMode() else restoreWidget(entry.id) }
        } else {
            (v as? LauncherWidgetHostView)?.blockContent = ui.editMode
            v.setOnClickListener { if (ui.editMode) exitEditMode() }
        }
    }

    /** Tells each widget its size in dp when it is new or its cells changed. */
    private fun applyWidgetSizes(st: HomeState) {
        val sizes = widgetSizes() ?: return
        fun apply(v: View?, sx: Int, sy: Int) {
            val hv = v as? LauncherWidgetHostView ?: return
            val key = sx * 100 + sy
            if (hv.getTag(R.id.tag_widget_size) == key) return
            hv.setTag(R.id.tag_widget_size, key)
            sizes.applySize(hv, sx, sy)
        }
        for (e in st.entries) when (e) {
            is WidgetEntry -> apply(views[e.id], e.placed.spanX, e.placed.spanY)
            is StackEntry -> e.widgets.forEach { w -> apply(widgetView(w), e.placed.spanX, e.placed.spanY) }
            else -> Unit
        }
    }

    /** Cell geometry of the pages, once laid out. */
    private fun widgetSizes(): WidgetSizes? {
        val page = home.workspace.pageAt(home.workspace.currentPage) ?: home.workspace.pageAt(0) ?: return null
        if (page.cellWidth <= 0 || page.cellHeight <= 0) return null
        return WidgetSizes(page.cellWidth, page.cellHeight, page.columns, page.rows, resources.displayMetrics.density, resizeAnyWidget, widgetInset, pillMax)
    }

    private fun newIconView() = AppIconView(this, homeStyle).apply {
        setOnClickListener { v ->
            if (ui.editMode) { exitEditMode(); return@setOnClickListener }
            (v as AppIconView).app?.let { launchFromView(v, it) }
        }
        setOnLongClickListener { v -> onItemLongPress(v) }
    }

    private fun newFolderView() = FolderIconView(this, homeStyle).apply {
        listener = this@HomeActivity
        styleFolder(this)
        setOnLongClickListener { v -> onItemLongPress(v) }
    }

    /** Translucent (60%) so the wallpaper shows through the folder. */
    private fun folderDefaultColor() = if (isNight) 0x991C1C1E.toInt() else 0x99F4F4F6.toInt()

    /** Folders take the icon shape and the mode's border values. */
    private fun styleFolder(f: FolderIconView) {
        f.shape = iconShape
        f.darkSurface = isNight
        f.defaultColor = folderDefaultColor()
    }

    // --- Long-press: menu, and a drag if the finger moves ------------------------------------

    private fun entryOf(v: View): HomeEntry? = views.entries.firstOrNull { it.value === v }?.key?.let { state?.entry(it) }

    private fun onItemLongPress(pressed: View): Boolean {
        // A widget inside a stack stands for the whole stack.
        val v = pressed.parent as? WidgetStackView ?: pressed
        val entry = entryOf(v) ?: return false
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dismissResizeFrame()
        ui.menu = ItemMenu(v.visualBounds(), menuFor(entry, v))
        if (entry is WidgetEntry || entry is StackEntry) showResizeFrame(v, entry)
        updateBackCallbacks()
        drag.arm(sourceFor(v, entry))
        return true
    }

    private fun menuFor(entry: HomeEntry, view: View? = null): List<MenuEntry> {
        val out = ArrayList<MenuEntry>()
        when (entry) {
            is WidgetEntry -> {
                if (widgets.canReconfigure(entry.appWidgetId)) {
                    out += MenuEntry(R.drawable.ph_widget_settings, getString(R.string.menu_widget_settings)) { widgets.reconfigure(entry.appWidgetId) }
                }
                if (entry.placed.container == Container.DESKTOP) {
                    out += MenuEntry(R.drawable.ph_stack, getString(R.string.menu_create_stack)) {
                        openPicker(DropAction.CreateStack(entry.id), entry.placed.spanX to entry.placed.spanY)
                    }
                }
                if (widgetBorder) {
                    val provider = entry.provider.flattenToString()
                    val shown = provider !in noOutline
                    out += MenuEntry(R.drawable.ph_eye, getString(if (shown) R.string.menu_hide_outline else R.string.menu_show_outline)) {
                        lifecycleScope.launch { launcher.settings.setWidgetOutline(provider, !shown) }
                    }
                }
                out += MenuEntry(R.drawable.ph_remove, getString(R.string.menu_remove)) { removeWithUndo(entry.id) }
            }
            is StackEntry -> {
                val front = entry.widgets.getOrNull((view as? WidgetStackView)?.current ?: 0) ?: entry.widgets.first()
                if (widgets.canReconfigure(front.appWidgetId)) {
                    out += MenuEntry(R.drawable.ph_widget_settings, getString(R.string.menu_widget_settings)) { widgets.reconfigure(front.appWidgetId) }
                }
                out += MenuEntry(R.drawable.ph_plus, getString(R.string.menu_add_to_stack)) {
                    openPicker(DropAction.AddToStack(entry.id), entry.placed.spanX to entry.placed.spanY)
                }
                out += MenuEntry(R.drawable.ph_remove, getString(R.string.menu_remove_from_stack)) {
                    edit(getString(R.string.snack_removed)) { launcher.home.remove(front.id) }
                }
                out += MenuEntry(R.drawable.ph_ungroup, getString(R.string.menu_unstack)) {
                    edit(getString(R.string.snack_unstacked)) { launcher.home.ungroup(entry.id) }
                }
                out += MenuEntry(R.drawable.ph_trash, getString(R.string.menu_remove_stack)) { removeWithUndo(entry.id) }
            }
            is AppEntry -> {
                val app = entry.app
                out += MenuEntry(R.drawable.ph_info, getString(R.string.menu_app_info)) { appInfo(app, null) }
                out += MenuEntry(R.drawable.ph_edit, getString(R.string.menu_edit_app)) { editApp(app) }
                if (ui.categoryMode) {
                    out += MenuEntry(R.drawable.ph_stack, getString(R.string.menu_send_to_category)) {
                        lifecycleScope.launch { launcher.home.moveToCategory(app.key, app.category ?: AppCategories.Category.OTHERS) }
                    }
                }
                if (drawerEnabled) {
                    out += MenuEntry(R.drawable.ph_remove, getString(R.string.menu_remove)) { removeWithUndo(entry.id) }
                } else {
                    out += MenuEntry(R.drawable.ph_hide, getString(R.string.menu_hide)) { hideWithUndo(app) }
                }
                if (!app.isSystem) out += MenuEntry(R.drawable.ph_trash, getString(R.string.menu_uninstall)) { uninstall(app) }
            }
            is FolderEntry -> {
                if (entry.placed.container == Container.DESKTOP) {
                    out += if (entry.isLarge) {
                        MenuEntry(R.drawable.ph_shrink, getString(R.string.menu_make_normal)) { setLarge(entry, false) }
                    } else {
                        MenuEntry(R.drawable.ph_enlarge, getString(R.string.menu_make_large)) { setLarge(entry, true) }
                    }
                }
                // A category folder stays while its category is organized: no ungroup or remove.
                if (!entry.isCategory) {
                    out += MenuEntry(R.drawable.ph_ungroup, getString(R.string.menu_ungroup)) {
                        edit(getString(R.string.snack_ungrouped)) { launcher.home.ungroup(entry.id) }
                    }
                    if (drawerEnabled) out += MenuEntry(R.drawable.ph_remove, getString(R.string.menu_remove)) { removeWithUndo(entry.id) }
                }
            }
        }
        return out
    }

    private fun sourceFor(v: View, entry: HomeEntry): DragSource {
        val scale = ViewMap.scaleOf(dragLayer, v)
        val local: RectF = when (v) {
            is AppIconView -> tmpRectF.apply { set(v.iconBounds(tmpRect)) }
            is FolderIconView -> v.backdropBounds(tmpRectF)
            else -> tmpRectF.apply { set(0f, 0f, v.width.toFloat(), v.height.toFloat()) }
        }
        pt[0] = local.left; pt[1] = local.top
        ViewMap.localToRoot(dragLayer, v, pt)
        val w = (local.width() * scale).toInt().coerceAtLeast(1)
        val h = (local.height() * scale).toInt().coerceAtLeast(1)
        val image: Bitmap = when (entry) {
            is AppEntry -> entry.app.icon
            else -> createBitmap(local.width().toInt().coerceAtLeast(1), local.height().toInt().coerceAtLeast(1)).also { b ->
                val c = Canvas(b)
                c.translate(-local.left, -local.top)
                v.draw(c)
                // Widgets: the same rounded shape they have on the page.
                WidgetFrame.clipSnapshot(v, b, local)
            }
        }
        val touchX = (dragLayer.lastDownX - pt[0]).coerceIn(0f, w.toFloat())
        val touchY = (dragLayer.lastDownY - pt[1]).coerceIn(0f, h.toFloat())
        val app = (entry as? AppEntry)?.app
        return DragSource(
            item = DragItem.Existing(entry.id),
            type = when (entry) {
                is FolderEntry -> ItemType.FOLDER
                is WidgetEntry -> ItemType.WIDGET
                is StackEntry -> ItemType.STACK
                else -> ItemType.APP
            },
            spanX = entry.placed.spanX, spanY = entry.placed.spanY,
            image = image, width = w, height = h, touchX = touchX, touchY = touchY,
            view = v, app = app,
        )
    }

    /** A drag image the size of a home icon, centred under the finger. */
    private fun newAppSource(item: DragItem, app: AppInfo, fromFolder: Long = 0): DragSource {
        val page = home.workspace.pageAt(home.workspace.currentPage)
        val cell = page?.cellWidth ?: 200
        val size = minOf((cell * homeStyle.iconCellFraction).toInt(), (homeStyle.maxIconDp * resources.displayMetrics.density).toInt())
        return DragSource(
            item = item, type = ItemType.APP, spanX = 1, spanY = 1,
            image = app.icon, width = size, height = size, touchX = size / 2f, touchY = size / 2f,
            view = null, app = app, fromFolder = fromFolder,
        )
    }

    override fun armDrag(app: AppInfo, bounds: Rect?) = drag.arm(newAppSource(DragItem.NewApp(app.key), app))

    /** Long-press inside the folder popup: the icon's menu, and a drag if the finger moves. */
    /** A menu requested by the drawer (its items and its options button). */
    override fun showMenu(anchor: Rect, entries: List<MenuEntry>) {
        ui.menu = ItemMenu(anchor, entries)
        updateBackCallbacks()
    }

    override fun armDragFromFolder(app: AppEntry, folder: FolderEntry, bounds: Rect) {
        ui.menu = ItemMenu(bounds, menuFor(app))
        updateBackCallbacks()
        drag.arm(newAppSource(DragItem.Existing(app.id), app.app, fromFolder = folder.id))
    }

    override fun folderTarget(x: Float, y: Float): Int? {
        val f = ui.folder ?: return null
        val g = ui.folderGrid
        if (g.isEmpty || !g.contains(x, y)) return null
        val col = ((x - g.left) / (g.width() / 3f)).toInt().coerceIn(0, 2)
        val row = ((y - g.top) / (g.height() / 3f)).toInt().coerceIn(0, 2)
        return (ui.folderPage * 9 + row * 3 + col).coerceAtMost(f.apps.size - 1)
    }

    override fun leaveFolder() {
        ui.folderDragId = 0L
        closeFolder()
    }

    // --- DragController.Callbacks -------------------------------------------------------------

    override fun state(): HomeState? = state

    override fun onDragStarted(source: DragSource) {
        ui.menu = null
        // From a folder, the folder stays open so the app can be moved inside it.
        if (source.fromFolder != 0L) {
            ui.folderDragId = (source.item as? DragItem.Existing)?.id ?: 0L
        } else {
            ui.folder = null
        }
        ui.picker = null
        dismissResizeFrame()
        drawerModel.dragEpoch++
        if (!drawer.isClosed) drawer.close(animate = false)
        ui.dragging = true
        // An empty page at the end, unless the last page already is empty.
        val st = state
        if (st != null && source.item !is DragItem.Page && st.entries.any { it.placed.container == Container.DESKTOP && it.placed.pageIndex == st.pageCount - 1 }) {
            extraPage = true
        }
        syncPages()
        updateBackCallbacks()
    }

    override fun onDropped(source: DragSource, result: DropResult) {
        ui.dragging = false
        extraPage = false
        val st = state
        val existingId = (source.item as? DragItem.Existing)?.id
        val newWidget = source.item as? DragItem.NewWidget
        val movedPage = source.item as? DragItem.Page
        ui.folderDragId = 0L
        when (result) {
            is DropResult.InFolder -> if (existingId != null && source.fromFolder != 0L) {
                lifecycleScope.launch { launcher.home.moveInFolder(source.fromFolder, existingId, result.index) }
            }
            is DropResult.OnPage -> if (movedPage != null && st != null) {
                val to = result.index.coerceAtMost(st.pageCount - 1)
                if (to != movedPage.index) {
                    movePage(movedPage.index, to)
                    dragLayer.postDelayed({ home.workspace.snapToPage(to) }, 250)
                }
            }
            // A widget from the picker is bound (and maybe set up) before it is saved.
            is DropResult.OnGrid if newWidget != null -> {
                val w = pickerWidgets[newWidget.provider to newWidget.userSerial]
                if (result.action == DropAction.Reject) toast(getString(R.string.no_room))
                else if (w != null) addPickedWidget(w, result.action, source.spanX, source.spanY)
            }
            is DropResult.OnGrid -> when (val a = result.action) {
                is DropAction.Move -> {
                    val from = existingId?.let { st?.entry(it)?.placed }
                    val pageChanged = from != null && a.container == Container.DESKTOP && (from.container != Container.DESKTOP || from.pageIndex != a.pageIndex)
                    // Move the view now so it does not flash at its old place before the rebind.
                    source.view?.let { v -> optimisticMove(v, a) }
                    edit(if (pageChanged) getString(R.string.snack_moved_page, a.pageIndex + 1) else null) {
                        launcher.home.place(source.item, a.container, a.pageIndex, a.x, a.y)
                    }
                }
                is DropAction.CreateFolder -> edit(getString(R.string.snack_folder_created)) {
                    launcher.home.createFolder(source.item, a.targetId, getString(R.string.folder_default_title))
                }
                is DropAction.AddToFolder -> edit(null) { launcher.home.addToFolder(source.item, a.folderId) }
                is DropAction.CreateStack -> edit(getString(R.string.snack_stack_created)) { launcher.home.createStack(source.item, a.targetId) }
                is DropAction.AddToStack -> edit(null) { launcher.home.addToStack(source.item, a.stackId) }
                DropAction.Reject -> toast(getString(R.string.no_room))
            }
            DropResult.Cancelled -> Unit
        }
        // The icon reappears now only where it stays put or was moved above; when it went into a
        // folder or was removed, the rebind takes it away instead of flashing it at its old spot.
        val view = source.view
        val stays = result is DropResult.Cancelled || result is DropResult.OnPage ||
            (result is DropResult.OnGrid && (result.action is DropAction.Move || result.action == DropAction.Reject))
        if (view != null) {
            if (stays) view.visibility = View.VISIBLE
            else view.postDelayed({ if (!drag.isDragging && view.isInvisible) view.isVisible = true }, 800)
        }
        // Keep the extra page if the item was dropped on it; the rebind brings the real page.
        val toNewPage = (result as? DropResult.OnGrid)?.action.let { it is DropAction.Move && it.container == Container.DESKTOP && st != null && it.pageIndex >= st.pageCount }
        if (!toNewPage) syncPages()
        updateBackCallbacks()
    }

    private fun optimisticMove(v: View, a: DropAction.Move) {
        val target = if (a.container == Container.DOCK) home.hotseat else home.workspace.pageAt(a.pageIndex) ?: return
        val lp = v.layoutParams as? CellLayout.LayoutParams ?: return
        lp.cellX = a.x
        lp.cellY = a.y
        if (v.parent !== target) {
            (v.parent as? CellLayout)?.removeView(v)
            target.addView(v, lp)
        } else {
            v.requestLayout()
        }
    }

    // --- Widgets ------------------------------------------------------------------------------

    /** Opens the picker for the current page, or ([target]) to add to a widget or stack. */
    private fun openPicker(target: DropAction?, span: Pair<Int, Int>?) {
        val sizes = widgetSizes() ?: return
        val apps = launcher.apps.apps.value.orEmpty()
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.Default) { WidgetCatalog.load(this@HomeActivity, apps, sizes) }
            pickerWidgets = groups.flatMap { it.widgets }.associateBy { it.info.provider to widgets.serialOf(it.info.profile) }
            pickerTarget = target
            pickerSpan = span
            ui.menu = null
            ui.picker = WidgetPickerState(groups, span)
            updateBackCallbacks()
        }
    }

    override fun openWidgets() = openPicker(null, null)

    override fun closePicker() {
        ui.picker = null
        pickerTarget = null
        pickerSpan = null
        updateBackCallbacks()
    }

    /** Tap in the picker: onto the stack it was opened for, else the first free spot from the current page on. */
    override fun addWidget(widget: PickerWidget) {
        val target = pickerTarget
        val span = pickerSpan
        closePicker()
        if (target != null && span != null) {
            addPickedWidget(widget, target, span.first, span.second)
            return
        }
        val st = state ?: return
        val (page, cell) = LayoutResolver.findSlotForNewItem(
            st.entries.map { it.placed }, st.pageCount, st.columns, st.rows, home.workspace.currentPage, widget.spanX, widget.spanY,
        )
        addPickedWidget(widget, DropAction.Move(Container.DESKTOP, page, cell.x, cell.y), widget.spanX, widget.spanY)
        if (page != home.workspace.currentPage) dragLayer.postDelayed({ home.workspace.snapToPage(page) }, 300)
    }

    override fun armWidgetDrag(widget: PickerWidget, preview: ImageBitmap?, bounds: Rect) {
        val page = home.workspace.pageAt(home.workspace.currentPage) ?: return
        val scale = ViewMap.scaleOf(dragLayer, page)
        val w = (widget.spanX * page.cellWidth * scale).toInt().coerceAtLeast(1)
        val h = (widget.spanY * page.cellHeight * scale).toInt().coerceAtLeast(1)
        val image = preview?.asAndroidBitmap() ?: createBitmap(w, h).also { it.eraseColor(0x66FFFFFF) }
        drag.arm(
            DragSource(
                item = DragItem.NewWidget(widget.info.provider, widgets.serialOf(widget.info.profile)),
                type = ItemType.WIDGET, spanX = widget.spanX, spanY = widget.spanY,
                image = image, width = w, height = h, touchX = w / 2f, touchY = h / 2f,
                view = null, app = null,
            ),
        )
    }

    private fun addPickedWidget(w: PickerWidget, action: DropAction, spanX: Int, spanY: Int) {
        val sizes = widgetSizes() ?: return
        widgets.startAdd(w.info, spanX, spanY, action, sizes.options(spanX, spanY))
    }

    /** The widget is bound (and set up, if it asked): save it where it was dropped. */
    private fun onWidgetAdded(p: HomeWidgets.PendingAdd) {
        lifecycleScope.launch {
            if (p.restoreItemId != 0L) {
                launcher.home.rebindWidget(p.restoreItemId, p.appWidgetId)
            } else {
                launcher.home.addWidget(p.toNewWidget(widgets.serialOf(p.user)), p.action)
                ui.snack = Snack(getString(R.string.snack_widget_added)) { lifecycleScope.launch { launcher.home.undo() } }
            }
        }
    }

    /** A widget whose app was reinstalled: bind it again in the same place. */
    private fun restoreWidget(itemId: Long) {
        val entry = state?.widget(itemId) ?: return
        val info = widgets.installedProvider(entry.provider, entry.userSerial)
        val sizes = widgetSizes()
        if (info == null || sizes == null) {
            toast(getString(R.string.widget_unavailable))
            return
        }
        widgets.startAdd(info, entry.placed.spanX, entry.placed.spanY, DropAction.Reject, sizes.options(entry.placed.spanX, entry.placed.spanY), restoreItemId = itemId)
    }

    /** Apps with widgets were installed, updated or removed: placeholders may now work, and the picker changes. */
    private fun onWidgetProvidersChanged() {
        val all = state?.entries.orEmpty().flatMap { e ->
            when (e) {
                is WidgetEntry -> listOf(e)
                is StackEntry -> e.widgets
                else -> emptyList()
            }
        }
        // Widgets of uninstalled apps go away; the system already deleted their host ids.
        val removed = all.filter(widgets::appRemoved).map { it.id }
        if (removed.isNotEmpty()) lifecycleScope.launch { launcher.home.removeWidgets(removed) }
        val ids = all.map { it.id }.toSet()
        widgets.prune(ids, recheck = true)
        state?.let(::bind)
        if (ui.picker != null) openPicker(pickerTarget, pickerSpan)
    }

    private fun showResizeFrame(view: View, entry: HomeEntry) {
        val sizes = widgetSizes() ?: return
        val range = widgets.range(entry, sizes) ?: return
        if (!range.resizable) return
        val page = view.parent as? CellLayout ?: return
        val st = state ?: return
        val occupied = GridOccupancy(st.columns, st.rows)
        for (o in st.placedIn(Container.DESKTOP, entry.placed.pageIndex)) if (o.id != entry.id) occupied.mark(o.x, o.y, o.spanX, o.spanY)
        val frame = ResizeFrame(
            this, dragLayer, page, view, occupied, range,
            onStart = {
                drag.disarm()
                ui.menu = null
                updateBackCallbacks()
            },
            onResize = { x, y, sx, sy -> edit(null) { launcher.home.resize(entry.id, x, y, sx, sy) } },
            // Not while the touch that dismissed it is still being dispatched.
            onDismiss = { dragLayer.post(::dismissResizeFrame) },
        )
        dragLayer.addView(frame, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        resizeFrame = frame
        updateBackCallbacks()
    }

    private fun dismissResizeFrame() {
        val f = resizeFrame ?: return
        resizeFrame = null
        dragLayer.removeView(f)
        updateBackCallbacks()
    }

    // --- Edits with undo ---------------------------------------------------------------------

    /** Runs a layout edit; when [snack] is given, shows it with Undo afterwards. */
    private fun edit(snack: String?, block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
            if (snack != null) ui.snack = Snack(snack) { lifecycleScope.launch { launcher.home.undo() } }
        }
    }

    private fun removeWithUndo(id: Long) = edit(getString(R.string.snack_removed)) { launcher.home.remove(id) }

    private fun hideWithUndo(app: AppInfo) {
        drawerModel.setHidden(app, true)
        ui.snack = Snack(getString(R.string.snack_hidden, app.label)) { drawerModel.setHidden(app, false) }
    }

    private fun setLarge(folder: FolderEntry, large: Boolean) {
        lifecycleScope.launch {
            if (!launcher.home.setFolderLarge(folder.id, large)) toast(getString(R.string.folder_no_room))
        }
    }

    // --- Folders -----------------------------------------------------------------------------

    override fun onOpenFolder(view: FolderIconView, folder: FolderEntry) {
        if (ui.editMode) { exitEditMode(); return }
        ui.folder = folder
        updateBackCallbacks()
    }

    override fun onLaunchFromFolder(view: View, app: AppEntry, bounds: Rect) {
        if (ui.editMode) { exitEditMode(); return }
        launchedFrom = WeakReference(view)
        val opts = ActivityOptions.makeClipRevealAnimation(dragLayer, bounds.left, bounds.top, bounds.width(), bounds.height()).toBundle()
        start(app.app, bounds, opts)
    }

    override fun launchFromFolder(app: AppEntry, bounds: Rect?) {
        launchedFrom = null
        val opts = bounds?.let { ActivityOptions.makeScaleUpAnimation(overlayView, it.left, it.top, it.width(), it.height()).toBundle() }
        start(app.app, bounds, opts)
        closeFolder()
    }

    override fun renameFolder(folder: FolderEntry, title: String) {
        lifecycleScope.launch { launcher.home.renameFolder(folder.id, title) }
    }

    override fun setFolderColor(folder: FolderEntry, color: Int) {
        lifecycleScope.launch { launcher.home.setFolderColor(folder.id, color) }
    }

    override fun closeFolder() {
        ui.folder = null
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        updateBackCallbacks()
    }

    // --- Edit mode and pages -----------------------------------------------------------------

    private fun enterEditMode() {
        if (!drawer.isClosed || ui.editMode) return
        ui.editMode = true
        syncPages()
        widgets.setEditMode(true)
        dismissResizeFrame()
        ui.currentPage = home.workspace.currentPage
        dragLayer.drawerEnabled = false
        if (hintVisible) home.hint.animate().alpha(0f).setDuration(120).start()
        home.setPageBackdrops(true)
        zoomHome(EDIT_SCALE)
        fade(home.hotseat, 0f)
        updateBackCallbacks()
    }

    private fun exitEditMode() {
        if (!ui.editMode) return
        ui.editMode = false
        syncPages()
        state?.let { st -> if (home.workspace.currentPage >= st.pageCount) home.workspace.snapToPage(st.pageCount - 1) }
        widgets.setEditMode(false)
        dismissResizeFrame()
        dragLayer.drawerEnabled = drawerEnabled
        if (hintVisible) home.hint.animate().alpha(1f).setDuration(150).start()
        home.setPageBackdrops(false)
        zoomHome(1f)
        fade(home.hotseat, 1f)
        updateBackCallbacks()
    }

    private fun fade(v: View, to: Float) {
        if (Motion.enabled()) v.animate().alpha(to).setDuration(200).start() else v.alpha = to
    }

    private fun zoomHome(scale: Float) {
        // Where the zoomed page's top edge lands, for the home/delete buttons above it.
        val ws = home.workspace
        val pivot = home.height / 2f
        ui.editScale = scale
        ui.editPageTop = (pivot - (pivot - ws.top) * scale).toInt()
        for (v in listOf(home.workspace, home.indicator)) {
            v.pivotY = home.height / 2f - v.top
            v.pivotX = v.width / 2f
            if (Motion.enabled()) {
                v.animate().scaleX(scale).scaleY(scale).setDuration(240).setInterpolator(decelerate).start()
            } else {
                v.scaleX = scale
                v.scaleY = scale
            }
        }
    }

    override fun openOverview() {
        ui.overview = captureThumbs()
        updateBackCallbacks()
    }

    override fun closeOverview() {
        ui.overview = null
        updateBackCallbacks()
    }

    /** Small images of each page for the overview (pages are drawn without their edit backdrop). */
    private fun captureThumbs(): List<PageThumb> {
        val out = ArrayList<PageThumb>()
        val count = state?.pageCount ?: home.workspace.childCount
        for (i in 0 until minOf(count, home.workspace.childCount)) {
            val page = home.workspace.pageAt(i) ?: continue
            if (page.width == 0 || page.height == 0) continue
            val s = THUMB_SCALE
            val b = createBitmap((page.width * s).toInt().coerceAtLeast(1), (page.height * s).toInt().coerceAtLeast(1))
            val c = Canvas(b)
            c.scale(s, s)
            val backdrop = page.showBackdrop
            page.showBackdrop = false
            page.draw(c)
            page.showBackdrop = backdrop
            out += PageThumb(i, b.asImageBitmap())
        }
        return out
    }

    override fun openPage(index: Int) {
        closeOverview()
        home.workspace.snapToPage(index)
    }

    /**
     * Pages on screen: the real ones, plus an empty one at the end while dragging (so items can
     * go onto a new page) or in edit mode (the plus page that adds one when tapped).
     */
    private fun syncPages() {
        val st = state ?: return
        home.setPageCount(st.pageCount + if (extraPage || ui.editMode) 1 else 0)
        home.setAddPage(ui.editMode && !drag.isDragging)
    }

    /** Edit mode: a long-press on a page picks it up; flip to where it should go and let go. */
    private fun armPageDrag() {
        val st = state ?: return
        val index = home.workspace.currentPage
        if (index >= st.pageCount) return // The plus page.
        val page = home.workspace.pageAt(index) ?: return
        val scale = ViewMap.scaleOf(dragLayer, page)
        val w = (page.width * scale).toInt().coerceAtLeast(1)
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        val image = createBitmap(w, h).also { b ->
            val c = Canvas(b)
            c.scale(scale, scale)
            page.draw(c)
        }
        pt[0] = 0f; pt[1] = 0f
        ViewMap.localToRoot(dragLayer, page, pt)
        drag.arm(
            DragSource(
                item = DragItem.Page(index), type = ItemType.APP, spanX = 1, spanY = 1,
                image = image, width = w, height = h,
                touchX = (dragLayer.lastDownX - pt[0]).coerceIn(0f, w.toFloat()),
                touchY = (dragLayer.lastDownY - pt[1]).coerceIn(0f, h.toFloat()),
                view = page, app = null,
            ),
        )
    }

    // --- Category mode ------------------------------------------------------------------------

    override fun categorizeApps() = edit(getString(R.string.snack_categorized)) { launcher.home.categorizeLooseApps() }

    /** Category mode just started: Google's search bar across the first free row of the home page. */
    private fun addSearchWidget() {
        val st = state ?: return
        lifecycleScope.launch {
            launcher.settings.setPendingSearchWidget(false)
            val info = widgets.installedProvider(HomeRepository.SEARCH_WIDGET, widgets.serialOf(android.os.Process.myUserHandle())) ?: return@launch
            val sizes = widgetSizes() ?: return@launch
            val grid = GridOccupancy(st.columns, st.rows)
            for (o in st.placedIn(Container.DESKTOP, 0)) grid.mark(o.x, o.y, o.spanX, o.spanY)
            val cell = grid.findFirstFree(st.columns, 1) ?: return@launch
            widgets.startAdd(info, st.columns, 1, DropAction.Move(Container.DESKTOP, 0, cell.x, cell.y), sizes.options(st.columns, 1))
        }
    }

    /** Home screen only mode: a swipe up opens the app chosen in settings (e.g. a search app). */
    private fun openSwipeTarget() {
        openTarget(swipeUpTarget ?: return, R.anim.swipe_up_enter)
    }

    /** Opens a chosen "action|package/class" target; false if it is gone. */
    private fun openTarget(target: String, enterAnim: Int = 0): Boolean {
        val action = target.substringBefore('|')
        val cn = ComponentName.unflattenFromString(target.substringAfter('|')) ?: return false
        val intent = Intent(action).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action == Intent.ACTION_MAIN) intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val opts = if (enterAnim != 0) ActivityOptions.makeCustomAnimation(this, enterAnim, 0).toBundle() else null
        return try {
            startActivity(intent, opts)
            true
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.swipe_up_unavailable))
            false
        } catch (e: SecurityException) {
            toast(getString(R.string.swipe_up_unavailable))
            false
        }
    }

    override fun makeHomePage(index: Int) {
        if (index <= 0) return
        edit(getString(R.string.snack_home_page)) { launcher.home.movePage(index, 0) }
        dragLayer.postDelayed({ home.workspace.snapToPage(0) }, 250)
    }

    override fun addPage() {
        lifecycleScope.launch {
            val index = launcher.home.addPage()
            if (ui.overview == null) dragLayer.post { home.workspace.snapToPage(index) }
        }
    }

    override fun deletePage(index: Int) = edit(getString(R.string.snack_page_deleted)) { launcher.home.deletePage(index) }

    override fun movePage(from: Int, to: Int) = edit(getString(R.string.snack_page_moved)) { launcher.home.movePage(from, to) }

    override fun chooseWallpaper() {
        // The wallpaper app chosen in settings, else Android's list of wallpaper apps.
        wallpaperTarget?.let { if (openTarget(it)) return }
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.wallpaper_unavailable))
        }
    }

    private fun updateBackCallbacks() {
        overlayBack.isEnabled = ui.isModal || ui.editMode || resizeFrame != null
    }

    // --- Wallpaper-aware colours ---------------------------------------------------------------

    private fun refreshWallpaperColors() {
        lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) { wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }
            wallpaperIsLight = colors != null && (
                (Build.VERSION.SDK_INT >= 31 && colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) ||
                    wallpaperLuminance(colors) > LIGHT_WALLPAPER
                )
            applyLabelColors()
            updateSystemBars(drawer.progress > 0.5f)
        }
    }

    /**
     * How light the wallpaper looks overall (0 black, 1 white), from its main colours weighted by
     * how much of it they cover. Some wallpapers (and some phones' wallpaper pickers) never set
     * the "supports dark text" hint, so a very light wallpaper is also recognised this way. A
     * normal app cannot read the wallpaper image itself on Android 13, but its colours are public.
     */
    private fun wallpaperLuminance(c: WallpaperColors): Float {
        val parts = listOfNotNull(c.primaryColor to 0.6f, c.secondaryColor?.let { it to 0.25f }, c.tertiaryColor?.let { it to 0.15f })
        val total = parts.sumOf { it.second.toDouble() }.toFloat()
        return parts.sumOf { (color, w) -> (color.luminance() * w).toDouble() }.toFloat() / total
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
        views.values.forEach {
            (it as? AppIconView)?.applyStyle()
            (it as? FolderIconView)?.let { f -> styleFolder(f); f.applyStyle() }
        }
        if (dark) {
            home.setSurfaceColors(0x1F000000, 0xE6FFFFFF.toInt(), 0xFF1C1B1F.toInt(), DARK_LABEL)
            home.setEditColors(backdrop = 0x1A000000, hint = 0x33000000)
        } else {
            home.setSurfaceColors(0x26FFFFFF, 0xE6202124.toInt(), 0xFFFFFFFF.toInt(), LIGHT_LABEL)
            home.setEditColors(backdrop = 0x24FFFFFF, hint = 0x40FFFFFF)
        }
    }

    /**
     * Fully clear system bars over the wallpaper: no translucent-bar flags, no contrast scrim, no
     * divider. Re-applied whenever the bar icons change colour, in case the system resets it.
     */
    @Suppress("DEPRECATION") // The translucent flags are deprecated but some device themes still set them.
    private fun clearBarScrims() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarDividerColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }

    private fun applyEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }

    private fun updateSystemBars(drawerMostlyOpen: Boolean) {
        clearBarScrims()
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
        // The system re-applies the window's bar settings for the new mode (bringing back the
        // contrast scrim, a dark strip behind the gesture bar) a moment after this call; the
        // transparent edge-to-edge bars are set again now and once the switch has settled.
        applyEdgeToEdge()
        updateSystemBars(drawer.progress > 0.5f)
        dragLayer.postDelayed({ clearBarScrims(); updateSystemBars(drawer.progress > 0.5f) }, BAR_RESET_DELAY_MS)
        // Widgets inflate their layout once, with the resources of that moment: on a light/dark
        // switch every widget view is made again so it draws in the new mode.
        if (isNight != lastNight) {
            lastNight = isNight
            widgets.prune(emptySet())
            state?.let(::bind)
        }
    }

    // --- Drawer callbacks --------------------------------------------------------------------

    override fun onDrawerOpened() {
        drawerBack.isEnabled = true
        drawerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (hintVisible) lifecycleScope.launch { launcher.settings.setDrawerHintSeen() }
    }

    override fun onDrawerClosed() {
        drawerBack.isEnabled = false
        // A menu opened from the drawer belongs to it.
        if (ui.menu != null) {
            ui.menu = null
            updateBackCallbacks()
        }
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

    // --- Return-to-home animation -----------------------------------------------------------

    /**
     * The system draws the closing app (a third-party launcher cannot take over that part), so
     * the home screen meets it: pages settle in from a slight zoom and the icon the app was
     * opened from grows back into place. Skipped if that icon is gone.
     */
    private fun playReturnAnimation(icon: View?) {
        if (!Motion.enabled() || !drawer.isClosed || ui.editMode || ui.isModal) return
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

    // --- Actions -----------------------------------------------------------------------------

    private fun launchFromView(v: View, app: AppInfo) {
        launchedFrom = WeakReference(v)
        val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle()
        start(app, v.windowBounds(), opts)
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

    // --- Per-app icon and name ----------------------------------------------------------------

    override fun editApp(app: AppInfo) {
        lifecycleScope.launch {
            val packs = withContext(Dispatchers.Default) { IconPack.installed(this@HomeActivity) }
            val s = launcher.settings.current()
            val original = s.iconOverrides[app.key.toString()]
            ui.menu = null
            ui.editApp = EditAppState(app, original, packs, showCategory = s.autoCategories && s.layoutMode == LayoutMode.HOME_ONLY)
            updateBackCallbacks()
        }
    }

    override fun pickIconImage() {
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.edit_app_image_failed))
        }
    }

    override fun saveEditApp(app: AppInfo, override: IconOverride) {
        val edit = ui.editApp
        ui.editApp = null
        updateBackCallbacks()
        val old = edit?.original?.file
        lifecycleScope.launch {
            launcher.settings.setIconOverride(app.key.toString(), override)
            if (old != null && old != override.file) IconFiles.delete(this@HomeActivity, old)
            // A new category moves the app into that category's folder.
            if (edit?.showCategory == true && override.category != edit.original?.category) {
                val cat = AppCategories.Category.fromKey(override.category)
                    ?: app.autoCategory ?: AppCategories.Category.OTHERS
                launcher.home.moveToCategory(app.key, cat)
            }
        }
    }

    override fun closeEditApp() {
        val edit = ui.editApp ?: return
        discardUnsavedImage(edit)
        ui.editApp = null
        updateBackCallbacks()
    }

    private fun discardUnsavedImage(edit: EditAppState) {
        val f = edit.draft.file
        if (f != null && f != edit.original?.file) IconFiles.delete(this, f)
    }

    override fun addToHome(app: AppInfo) {
        lifecycleScope.launch {
            val page = launcher.home.addToHome(app, home.workspace.currentPage)
            if (page >= 0) toast(getString(R.string.added_to_page, app.label, page + 1))
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Bounds in window coordinates (this activity's window is full screen). */
    /**
     * The part of an item people see (the icon or folder square, without the label or the
     * empty cell around it) in window coordinates, so a menu opens right next to it.
     */
    private fun View.visualBounds(): Rect {
        val local = when (this) {
            is AppIconView -> RectF(iconBounds(tmpRect))
            is FolderIconView -> backdropBounds(RectF())
            else -> return windowBounds()
        }
        val origin = IntArray(2).also { dragLayer.getLocationInWindow(it) }
        pt[0] = local.left; pt[1] = local.top
        ViewMap.localToRoot(dragLayer, this, pt)
        val l = pt[0]; val t = pt[1]
        pt[0] = local.right; pt[1] = local.bottom
        ViewMap.localToRoot(dragLayer, this, pt)
        return Rect((l + origin[0]).toInt(), (t + origin[1]).toInt(), (pt[0] + origin[0]).toInt(), (pt[1] + origin[1]).toInt())
    }

    private fun View.windowBounds(): Rect {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val s = ViewMap.scaleOf(dragLayer, this)
        return Rect(loc[0], loc[1], loc[0] + (width * s).toInt(), loc[1] + (height * s).toInt())
    }

    companion object {
        private const val LIGHT_LABEL = 0xFFFFFFFF.toInt()
        private const val DARK_LABEL = 0xFF1C1B1F.toInt()
        private const val LABEL_SHADOW = 0x8A000000.toInt()
        private const val FADE_IN_MS = 180L
        private const val EDIT_SCALE = 0.74f
        private const val BAR_RESET_DELAY_MS = 350L
        /** Above this overall lightness a wallpaper gets dark labels (light mode). */
        private const val LIGHT_WALLPAPER = 0.62f
        private const val RETURN_SCALE = 1.06f
        private const val THUMB_SCALE = 0.4f
    }
}
