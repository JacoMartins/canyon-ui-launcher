package com.example.homelauncher

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.homelauncher.data.AppEntry
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.data.DragItem
import com.example.homelauncher.data.FolderEntry
import com.example.homelauncher.data.HomeEntry
import com.example.homelauncher.data.HomeState
import com.example.homelauncher.model.Container
import com.example.homelauncher.model.DropAction
import com.example.homelauncher.model.ItemType
import com.example.homelauncher.ui.drawer.AppDrawer
import com.example.homelauncher.ui.drawer.DrawerActions
import com.example.homelauncher.ui.drawer.DrawerModel
import com.example.homelauncher.ui.home.AppIconView
import com.example.homelauncher.ui.home.CellLayout
import com.example.homelauncher.ui.home.DragController
import com.example.homelauncher.ui.home.DragLayer
import com.example.homelauncher.ui.home.DragSource
import com.example.homelauncher.ui.home.DrawerController
import com.example.homelauncher.ui.home.DropResult
import com.example.homelauncher.ui.home.FolderIconView
import com.example.homelauncher.ui.home.HomeScreenLayout
import com.example.homelauncher.ui.home.ViewMap
import com.example.homelauncher.ui.overlay.HomeOverlay
import com.example.homelauncher.ui.overlay.ItemMenu
import com.example.homelauncher.ui.overlay.MenuEntry
import com.example.homelauncher.ui.overlay.OverlayActions
import com.example.homelauncher.ui.overlay.OverlayState
import com.example.homelauncher.ui.overlay.PageThumb
import com.example.homelauncher.ui.overlay.Snack
import com.example.homelauncher.ui.theme.HomeTheme
import com.example.homelauncher.util.Motion
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

    private val tmpRect = Rect()
    private val tmpRectF = RectF()
    private val pt = FloatArray(2)

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        launcherApps = getSystemService(LauncherApps::class.java)

        home = HomeScreenLayout(this, 4, 6, 4)
        // Content fades in once on the first bind so a cold start never shows a half-built page.
        home.workspace.alpha = 0f
        home.hotseat.alpha = 0f
        home.indicator.alpha = 0f
        home.workspace.onScroll = home.indicator::setPosition
        home.workspace.onPageSettled = { ui.currentPage = it }
        home.onEmptyLongPress = ::enterEditMode
        home.onEmptyTap = { if (ui.editMode) exitEditMode() }

        drawerModel = DrawerModel(launcher.apps, launcher.settings, lifecycleScope)

        dragLayer = DragLayer(this)
        drawerView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.INVISIBLE // Composed and laid out up front so the first open is smooth.
            setContent {
                HomeTheme { AppDrawer(drawerModel, dragLayer.drawerTouch, this@HomeActivity) }
            }
        }
        overlayView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { HomeTheme { HomeOverlay(ui, this@HomeActivity) } }
        }
        dragLayer.addView(home, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        dragLayer.addView(drawerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        dragLayer.addView(overlayView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        drawer = DrawerController(home, drawerView, this)
        dragLayer.drawer = drawer
        drag = DragController(dragLayer, home, this)
        dragLayer.drag = drag
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
        drag.cancel()
        wallpaperManager.removeOnColorsChangedListener(colorsListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed. If home was already showing, close what is open, animate back to the
        // first page or close the drawer; if returning from another app, reset without animation.
        val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (ui.isModal || ui.editMode) {
                ui.menu = null
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
        home.setPageCount(st.pageCount + if (extraPage) 1 else 0)
        ui.pageCount = st.pageCount
        ui.currentPage = home.workspace.currentPage

        val dragged = drag.source?.view
        val seen = HashSet<Long>(st.entries.size)
        for (entry in st.entries) {
            seen += entry.id
            val p = entry.placed
            val view = viewFor(entry)
            val parent: CellLayout = if (p.container == Container.DOCK) home.hotseat else home.workspace.pageAt(p.pageIndex) ?: continue
            val style = if (p.container == Container.DOCK) dockStyle else homeStyle
            when (view) {
                is AppIconView -> { view.bind((entry as AppEntry).app); view.useStyle(style) }
                is FolderIconView -> { view.bind(entry as FolderEntry); view.useStyle(style) }
            }
            val lp = view.layoutParams as? CellLayout.LayoutParams
            if (view.parent !== parent) {
                (view.parent as? CellLayout)?.removeView(view)
                parent.addView(view, CellLayout.LayoutParams(p.x, p.y, p.spanX, p.spanY))
            } else if (lp != null && (lp.cellX != p.x || lp.cellY != p.y || lp.spanX != p.spanX || lp.spanY != p.spanY)) {
                lp.cellX = p.x; lp.cellY = p.y; lp.spanX = p.spanX; lp.spanY = p.spanY
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
        val wantFolder = entry is FolderEntry
        if (existing != null && (existing is FolderIconView) == wantFolder) return existing
        (existing?.parent as? CellLayout)?.removeView(existing)
        val v = if (wantFolder) newFolderView() else newIconView()
        views[entry.id] = v
        return v
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
        defaultColor = folderDefaultColor()
        setOnLongClickListener { v -> onItemLongPress(v) }
    }

    private fun folderDefaultColor() = if (isNight) 0xD91C1C1E.toInt() else 0xCCF4F4F6.toInt()

    // --- Long-press: menu, and a drag if the finger moves ------------------------------------

    private fun entryOf(v: View): HomeEntry? = views.entries.firstOrNull { it.value === v }?.key?.let { state?.entry(it) }

    private fun onItemLongPress(v: View): Boolean {
        val entry = entryOf(v) ?: return false
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        ui.menu = ItemMenu(v.windowBounds(), menuFor(entry))
        updateBackCallbacks()
        drag.arm(sourceFor(v, entry))
        return true
    }

    private fun menuFor(entry: HomeEntry): List<MenuEntry> {
        val out = ArrayList<MenuEntry>()
        when (entry) {
            is AppEntry -> {
                val app = entry.app
                out += MenuEntry(R.drawable.ph_info, getString(R.string.menu_app_info)) { appInfo(app, null) }
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
                out += MenuEntry(R.drawable.ph_ungroup, getString(R.string.menu_ungroup)) {
                    edit(getString(R.string.snack_ungrouped)) { launcher.home.ungroup(entry.id) }
                }
                if (drawerEnabled) out += MenuEntry(R.drawable.ph_remove, getString(R.string.menu_remove)) { removeWithUndo(entry.id) }
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
            is FolderEntry -> createBitmap(local.width().toInt().coerceAtLeast(1), local.height().toInt().coerceAtLeast(1)).also { b ->
                val c = Canvas(b)
                c.translate(-local.left, -local.top)
                v.draw(c)
            }
        }
        val touchX = (dragLayer.lastDownX - pt[0]).coerceIn(0f, w.toFloat())
        val touchY = (dragLayer.lastDownY - pt[1]).coerceIn(0f, h.toFloat())
        val app = (entry as? AppEntry)?.app
        return DragSource(
            item = DragItem.Existing(entry.id),
            type = if (entry is FolderEntry) ItemType.FOLDER else ItemType.APP,
            spanX = entry.placed.spanX, spanY = entry.placed.spanY,
            image = image, width = w, height = h, touchX = touchX, touchY = touchY,
            view = v, app = app,
        )
    }

    /** A drag image the size of a home icon, centred under the finger. */
    private fun newAppSource(item: DragItem, app: AppInfo): DragSource {
        val page = home.workspace.pageAt(home.workspace.currentPage)
        val cell = page?.cellWidth ?: 200
        val size = minOf((cell * homeStyle.iconCellFraction).toInt(), (homeStyle.maxIconDp * resources.displayMetrics.density).toInt())
        return DragSource(
            item = item, type = ItemType.APP, spanX = 1, spanY = 1,
            image = app.icon, width = size, height = size, touchX = size / 2f, touchY = size / 2f,
            view = null, app = app,
        )
    }

    override fun armDrag(app: AppInfo, bounds: Rect?) = drag.arm(newAppSource(DragItem.NewApp(app.key), app))

    /** Long-press inside the folder popup: the icon's menu, and a drag if the finger moves. */
    override fun armDragFromFolder(app: AppEntry, folder: FolderEntry, bounds: Rect) {
        ui.menu = ItemMenu(bounds, menuFor(app))
        updateBackCallbacks()
        drag.arm(newAppSource(DragItem.Existing(app.id), app.app))
    }

    // --- DragController.Callbacks -------------------------------------------------------------

    override fun state(): HomeState? = state

    override fun onDragStarted(source: DragSource) {
        ui.menu = null
        ui.folder = null
        drawerModel.dragEpoch++
        if (!drawer.isClosed) drawer.close(animate = false)
        ui.dragging = true
        // An empty page at the end, unless the last page already is empty.
        val st = state
        if (st != null && st.entries.any { it.placed.container == Container.DESKTOP && it.placed.pageIndex == st.pageCount - 1 }) {
            extraPage = true
            home.setPageCount(st.pageCount + 1)
        }
        updateBackCallbacks()
    }

    override fun onDropped(source: DragSource, result: DropResult) {
        ui.dragging = false
        extraPage = false
        val st = state
        val existingId = (source.item as? DragItem.Existing)?.id
        when (result) {
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
                DropAction.Reject -> toast(getString(R.string.no_room))
            }
            DropResult.Cancelled -> Unit
        }
        // The icon reappears now only where it stays put or was moved above; when it went into a
        // folder or was removed, the rebind takes it away instead of flashing it at its old spot.
        val view = source.view
        val stays = result is DropResult.Cancelled ||
            (result is DropResult.OnGrid && (result.action is DropAction.Move || result.action == DropAction.Reject))
        if (view != null) {
            if (stays) view.visibility = View.VISIBLE
            else view.postDelayed({ if (!drag.isDragging && view.isInvisible) view.isVisible = true }, 800)
        }
        // Keep the extra page if the item was dropped on it; the rebind brings the real page.
        val toNewPage = (result as? DropResult.OnGrid)?.action.let { it is DropAction.Move && it.container == Container.DESKTOP && st != null && it.pageIndex >= st.pageCount }
        if (!toNewPage) st?.let { home.setPageCount(it.pageCount) }
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

    override fun addPage() {
        lifecycleScope.launch {
            val index = launcher.home.addPage()
            if (ui.overview == null) dragLayer.post { home.workspace.snapToPage(index) }
        }
    }

    override fun deletePage(index: Int) = edit(getString(R.string.snack_page_deleted)) { launcher.home.deletePage(index) }

    override fun movePage(from: Int, to: Int) = edit(getString(R.string.snack_page_moved)) { launcher.home.movePage(from, to) }

    override fun chooseWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.wallpaper_unavailable))
        }
    }

    private fun updateBackCallbacks() {
        overlayBack.isEnabled = ui.isModal || ui.editMode
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
        views.values.forEach {
            (it as? AppIconView)?.applyStyle()
            (it as? FolderIconView)?.let { f -> f.defaultColor = folderDefaultColor(); f.applyStyle() }
        }
        if (dark) {
            home.setSurfaceColors(0x1F000000, 0xE6FFFFFF.toInt(), 0xFF1C1B1F.toInt(), DARK_LABEL)
            home.setEditColors(backdrop = 0x1A000000, hint = 0x33000000)
        } else {
            home.setSurfaceColors(0x26FFFFFF, 0xE6202124.toInt(), 0xFFFFFFFF.toInt(), LIGHT_LABEL)
            home.setEditColors(backdrop = 0x24FFFFFF, hint = 0x40FFFFFF)
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
        drawerBack.isEnabled = true
        drawerView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (hintVisible) lifecycleScope.launch { launcher.settings.setDrawerHintSeen() }
    }

    override fun onDrawerClosed() {
        drawerBack.isEnabled = false
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

    override fun addToHome(app: AppInfo) {
        lifecycleScope.launch {
            val page = launcher.home.addToHome(app, home.workspace.currentPage)
            if (page >= 0) toast(getString(R.string.added_to_page, app.label, page + 1))
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Bounds in window coordinates (this activity's window is full screen). */
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
        private const val EDIT_SCALE = 0.82f
        private const val RETURN_SCALE = 1.06f
        private const val THUMB_SCALE = 0.4f
    }
}
