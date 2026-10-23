package com.example.homelauncher.ui.overlay

import android.graphics.Rect
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp
import com.example.homelauncher.R
import com.example.homelauncher.data.AppEntry
import com.example.homelauncher.data.FolderEntry
import com.example.homelauncher.ui.design.PrimaryButton
import com.example.homelauncher.ui.design.SecondaryButton
import com.example.homelauncher.ui.design.MenuLayer
import com.example.homelauncher.ui.design.Spacing
import com.example.homelauncher.ui.design.buttonRing
import com.example.homelauncher.ui.design.surfaceRing
import com.example.homelauncher.ui.home.AppIconView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Folder colours offered in the folder popup (0 = theme default). */
val FolderColors = listOf(0, 0xFF5B8DEF.toInt(), 0xFF3FB27F.toInt(), 0xFFF2B63D.toInt(), 0xFFF0805A.toInt(), 0xFFE86A8F.toInt(), 0xFF9B7BE8.toInt())

private val PanelShape = RoundedCornerShape(32.dp)

@Composable
fun HomeOverlay(state: OverlayState, actions: OverlayActions) {
    Box(Modifier.fillMaxSize()) {
        val chrome = state.editMode && state.overview == null && state.folder == null && state.picker == null && !state.dragging
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) { EditTopBar(state, actions) }
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { EditBottomBar(actions) }

        state.picker?.let { WidgetPicker(it, actions) }
        state.editApp?.let { EditAppSheet(it, actions) }
        state.folder?.let { FolderPopup(it, actions) }
        state.menu?.let { ItemMenuLayer(it) { state.menu = null } }
        state.overview?.let { PageOverview(it, state, actions) }
        SnackHost(state, Modifier.align(Alignment.BottomCenter))
    }
}

// ------------------------------------------------------------------------------- edit mode

/**
 * Just above the focused page in edit mode: make it the home page (left; a filled house when it
 * already is) and delete it (right). Only the two buttons are drawn and take touches; the row
 * between them is empty space. Hidden on the plus page at the end.
 */
@Composable
private fun EditTopBar(state: OverlayState, actions: OverlayActions) {
    val page = state.currentPage
    if (page >= state.pageCount) return
    val isHome = page == HOME_PAGE
    val density = LocalDensity.current
    val minTop = WindowInsets.statusBars.getTop(density)
    val gap = with(density) { Spacing.sm.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth(state.editScale)
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                // Bottom edge a little above the page; never under the status bar.
                val y = (state.editPageTop - p.height - gap).coerceAtLeast(minTop + gap)
                layout(p.width, y + p.height) { p.place(0, y) }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PageIconButton(
            icon = if (isHome) R.drawable.ph_house_fill else R.drawable.ph_house,
            label = stringResource(if (isHome) R.string.home_page else R.string.make_home_page),
            enabled = !isHome,
        ) { actions.makeHomePage(page) }
        PageIconButton(R.drawable.ph_trash, stringResource(R.string.delete_page), enabled = !isHome && state.pageCount > 1) {
            actions.deletePage(page)
        }
    }
}

/** A round button with its own surface and ring (OneDepth secondary style), icon only. */
@Composable
private fun PageIconButton(@DrawableRes icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = colors.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.38f)
    // Material pads clickable surfaces out to a 48dp touch target, which sets the ring off the
    // circle; the 44dp button is its own target here.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = colors.secondaryContainer.copy(alpha = if (enabled) 1f else 0.7f),
            modifier = Modifier
                .size(44.dp)
                .buttonRing()
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}


@Composable
private fun EditBottomBar(actions: OverlayActions) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = Spacing.lg)
            .shadow(Spacing.sm, PanelShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.375f))
            .surfaceRing(cornerRadius = 32.dp),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
    ) {
        Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ToolButton(R.drawable.ph_wallpaper, stringResource(R.string.wallpaper), actions::chooseWallpaper)
            ToolButton(R.drawable.ph_widgets, stringResource(R.string.widgets), actions::openWidgets)
            ToolButton(R.drawable.ph_pages, stringResource(R.string.pages), actions::openOverview)
            ToolButton(R.drawable.ph_settings, stringResource(R.string.settings), actions::openSettings)
        }
    }
}

@Composable
private fun ToolButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xsSm),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ------------------------------------------------------------------------------- long-press menu

@Composable
private fun ItemMenuLayer(menu: ItemMenu, onDismiss: () -> Unit) = MenuLayer(menu.anchor, menu.entries, onDismiss)

// ------------------------------------------------------------------------------- folder popup

private class Coords {
    var value: LayoutCoordinates? = null

    fun bounds(): Rect? {
        val c = value?.takeIf { it.isAttached } ?: return null
        val b = c.boundsInWindow()
        return Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
    }
}

private const val FOLDER_COLUMNS = 4
private const val FOLDER_PER_PAGE = 16

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderPopup(folder: FolderEntry, actions: OverlayActions) {
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    var showColors by remember(folder.id) { mutableStateOf(false) }
    fun close() {
        if (title.trim() != folder.title) actions.renameFolder(folder, title)
        actions.closeFolder()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
            .windowInsetsPadding(WindowInsets.ime),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { } // keep taps inside
                .surfaceRing(cornerRadius = 32.dp),
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        ) {
            Column(Modifier.padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    val nameLabel = stringResource(R.string.folder_name)
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (title.trim() != folder.title) actions.renameFolder(folder, title) }),
                        modifier = Modifier.weight(1f).semantics { contentDescription = nameLabel },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (title.isEmpty()) Text(stringResource(R.string.folder_default_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { showColors = !showColors },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ph_palette), contentDescription = stringResource(R.string.folder_color), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(visible = showColors) {
                    Row(Modifier.padding(top = Spacing.smMd), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        for (c in FolderColors) {
                            val selected = folder.color == c
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                                    .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable(role = Role.RadioButton) { actions.setFolderColor(folder, c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(painterResource(R.drawable.ph_check), contentDescription = null, modifier = Modifier.size(16.dp), tint = if (c == 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))

                val pages = (folder.apps.size + FOLDER_PER_PAGE - 1) / FOLDER_PER_PAGE
                val pager = rememberPagerState { pages.coerceAtLeast(1) }
                // Every row has the same fixed height, and a folder with several pages keeps room
                // for all 16 apps on each, so the panel does not change size between pages.
                val rowHeight = Spacing.smMd * 2 + 56.dp + AppIconView.LABEL_GAP_DP.dp +
                    with(LocalDensity.current) { (AppIconView.LABEL_SP + 3).sp.toDp() }
                val rowsPerPage = if (pages > 1) FOLDER_PER_PAGE / FOLDER_COLUMNS else (folder.apps.size + FOLDER_COLUMNS - 1) / FOLDER_COLUMNS
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    val apps = folder.apps.drop(page * FOLDER_PER_PAGE).take(FOLDER_PER_PAGE)
                    Column(Modifier.padding(horizontal = Spacing.sm).height(rowHeight * rowsPerPage), verticalArrangement = Arrangement.Top) {
                        for (row in apps.chunked(FOLDER_COLUMNS)) {
                            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                                for (app in row) FolderApp(app, folder, actions, Modifier.weight(1f))
                                repeat(FOLDER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (pages > 1) {
                    Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xsSm)) {
                        repeat(pages) { i ->
                            val active = pager.currentPage == i
                            Box(
                                Modifier
                                    .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.9f else 0.35f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderApp(app: AppEntry, folder: FolderEntry, actions: OverlayActions, modifier: Modifier) {
    val coords = remember { Coords() }
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .onPlaced { coords.value = it }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = { actions.launchFromFolder(app, coords.bounds()) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    coords.bounds()?.let { actions.armDragFromFolder(app, folder, it) }
                },
            )
            .padding(vertical = Spacing.smMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.app.image, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(AppIconView.LABEL_GAP_DP.dp))
        Text(
            app.app.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = AppIconView.LABEL_SP.sp, lineHeight = (AppIconView.LABEL_SP + 3).sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}

// ------------------------------------------------------------------------------- page overview

@Composable
private fun PageOverview(thumbs: List<PageThumb>, state: OverlayState, actions: OverlayActions) {
    val haptics = LocalHapticFeedback.current
    val bounds = remember { HashMap<Int, androidx.compose.ui.geometry.Rect>() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions::closeOverview)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
            Text(
                stringResource(R.string.pages),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(top = Spacing.lg, start = Spacing.xs),
            )
            Text(
                stringResource(R.string.page_overview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.md),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(thumbs, key = { _, t -> t.index }) { i, thumb ->
                    val isDragged = dragging == i
                    Box(
                        Modifier
                            .onGloballyPositioned { bounds[i] = it.boundsInWindow() }
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragOffset.x; translationY = dragOffset.y
                                    scaleX = 1.05f; scaleY = 1.05f
                                }
                            }
                            .pointerInput(thumbs.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); dragging = i; dragOffset = Offset.Zero },
                                    onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                    onDragEnd = {
                                        val from = dragging
                                        val center = bounds[from]?.center?.plus(dragOffset)
                                        val to = center?.let { c -> bounds.entries.firstOrNull { it.value.contains(c) }?.key } ?: from
                                        dragging = -1
                                        dragOffset = Offset.Zero
                                        if (to != from) actions.movePage(from, to)
                                    },
                                    onDragCancel = { dragging = -1; dragOffset = Offset.Zero },
                                )
                            },
                    ) {
                        PageCard(thumb, current = state.currentPage == thumb.index, canDelete = thumbs.size > 1 && thumb.index != HOME_PAGE, actions)
                    }
                }
                item(key = "add") {
                    Surface(
                        onClick = actions::addPage,
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.aspectRatio(0.56f).surfaceRing(cornerRadius = 24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ph_plus), contentDescription = stringResource(R.string.add_page), tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCard(thumb: PageThumb, current: Boolean, canDelete: Boolean, actions: OverlayActions) {
    val label = stringResource(R.string.page_n, thumb.index + 1)
    Column {
        Surface(
            onClick = { actions.openPage(thumb.index) },
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = if (current) 0.22f else 0.12f),
            modifier = Modifier
                .aspectRatio(0.56f)
                .border(if (current) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                .surfaceRing(cornerRadius = 24.dp)
                .semantics { contentDescription = label },
        ) {
            Image(thumb.image, contentDescription = null, modifier = Modifier.fillMaxSize().padding(Spacing.sm))
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f).padding(start = Spacing.xs))
            if (canDelete) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { actions.deletePage(thumb.index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ph_trash), contentDescription = stringResource(R.string.delete_page), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------- snackbar

@Composable
private fun SnackHost(state: OverlayState, modifier: Modifier) {
    val snack = state.snack
    LaunchedEffect(snack?.id) {
        if (snack != null) {
            delay(SNACK_MS)
            if (state.snack === snack) state.snack = null
        }
    }
    AnimatedVisibility(
        visible = snack != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier,
    ) {
        val s = snack ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 128.dp, start = Spacing.md, end = Spacing.md)
                .shadow(Spacing.sm, PanelShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.375f))
                .surfaceRing(cornerRadius = 32.dp),
            shape = PanelShape,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
        ) {
            Row(Modifier.padding(start = Spacing.mdLg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(end = Spacing.md))
                val undo = s.onUndo
                if (undo != null) PrimaryButton(stringResource(R.string.undo), { state.snack = null; undo() })
            }
        }
    }
}

private const val SNACK_MS = 5000L

/** The first page is the home page (the house in the page indicator); it cannot be deleted. */
private const val HOME_PAGE = 0
