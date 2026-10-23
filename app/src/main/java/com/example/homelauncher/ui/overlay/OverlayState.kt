package com.example.homelauncher.ui.overlay

import android.graphics.Rect
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.example.homelauncher.data.AppEntry
import com.example.homelauncher.data.FolderEntry

class MenuEntry(@DrawableRes val icon: Int, val label: String, val action: () -> Unit)

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
    fun openSettings()
    fun openOverview()
    fun closeOverview()
    fun openPage(index: Int)
    fun addPage()
    fun deletePage(index: Int)
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
    var overview by mutableStateOf<List<PageThumb>?>(null)
    var currentPage by mutableIntStateOf(0)
    var pageCount by mutableIntStateOf(1)

    /** True while something modal (menu, folder, overview) covers the home screen. */
    val isModal get() = menu != null || folder != null || overview != null
}
