package io.github.jacomartins.canyonui.ui.overlay

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.jacomartins.canyonui.ui.design.MenuEntry
import io.github.jacomartins.canyonui.ui.widget.PickerWidget
import io.github.jacomartins.canyonui.data.AppEntry
import io.github.jacomartins.canyonui.data.AppInfo
import io.github.jacomartins.canyonui.data.FolderEntry
import io.github.jacomartins.canyonui.data.IconOverride


/** A long-press menu next to [anchor] (window coordinates). */
class ItemMenu(val anchor: Rect, val entries: List<MenuEntry>)

class Snack(val message: String, val onUndo: (() -> Unit)?) {
    val id = System.nanoTime()
}

class PageThumb(val index: Int, val image: ImageBitmap)

/** What the overlay asks the activity to do. */
interface OverlayActions {
    fun launchFromFolder(app: AppEntry, bounds: Rect?)
    fun armDragFromFolder(app: AppEntry, folder: FolderEntry, bounds: Rect)
    fun renameFolder(folder: FolderEntry, title: String)
    fun setFolderColor(folder: FolderEntry, color: Int)
    fun closeFolder()
    fun chooseWallpaper()
    fun openWidgets()
    fun closeEditApp()
    fun saveEditApp(app: AppInfo, override: IconOverride)
    fun pickIconImage()
    fun closePicker()
    fun addWidget(widget: PickerWidget)
    fun armWidgetDrag(widget: PickerWidget, preview: ImageBitmap?, bounds: Rect)
    fun openSettings()
    fun openOverview()
    fun closeOverview()
    fun openPage(index: Int)
    fun addPage()
    fun deletePage(index: Int)
    fun makeHomePage(index: Int)
    fun categorizeApps()
    fun movePage(from: Int, to: Int)
}

/**
 * Everything the overlay shows, as Compose state written from the activity on the main thread.
 */
class OverlayState {
    var menu by mutableStateOf<ItemMenu?>(null)
    var folder by mutableStateOf<FolderEntry?>(null)
    var snack by mutableStateOf<Snack?>(null)
    var dragging by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    /** "Organize apps by category" is on (home screen only mode). */
    var categoryMode by mutableStateOf(false)
    /** Edit mode: the zoomed page's top edge (window px) and its zoom, for the bar above it. */
    var editPageTop by mutableIntStateOf(0)
    var editScale by mutableFloatStateOf(1f)
    var overview by mutableStateOf<List<PageThumb>?>(null)
    var picker by mutableStateOf<WidgetPickerState?>(null)
    var editApp by mutableStateOf<EditAppState?>(null)
    var currentPage by mutableIntStateOf(0)
    var pageCount by mutableIntStateOf(1)

    /** True while something modal (menu, folder, overview, widget picker) covers the home screen. */
    val isModal get() = menu != null || folder != null || overview != null || picker != null || editApp != null
}
