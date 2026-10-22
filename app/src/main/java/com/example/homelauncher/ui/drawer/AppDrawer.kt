package com.example.homelauncher.ui.drawer

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.example.homelauncher.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.data.DrawerStyle
import com.example.homelauncher.ui.common.Glyphs
import com.example.homelauncher.ui.home.DragLayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the drawer asks the activity to do. */
interface DrawerActions {
    fun launch(app: AppInfo, bounds: Rect?)
    fun appInfo(app: AppInfo, bounds: Rect?)
    fun uninstall(app: AppInfo)
    fun addToHome(app: AppInfo)
}

private const val COLUMNS = 5

@Composable
fun AppDrawer(model: DrawerModel, touch: DragLayer.DrawerTouchState, actions: DrawerActions) {
    val results by model.results.collectAsState()
    val sections by model.sections.collectAsState()
    val style by model.style.collectAsState()
    var showHidden by remember { mutableStateOf(false) }
    val searching = model.query.isNotBlank()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // The parent closes the drawer on a downward drag only when the list is at its top.
    LaunchedEffect(style) {
        snapshotFlow { if (style == DrawerStyle.GRID) gridState.canScrollBackward else listState.canScrollBackward }
            .collect { touch.contentAtTop = !it }
    }
    // A new query starts at the top.
    LaunchedEffect(Unit) {
        snapshotFlow { model.query }.collect {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SearchBar(
            model = model,
            style = style,
            onStyle = model::setStyle,
            onShowHidden = { showHidden = true },
            onGo = { results.firstOrNull()?.let { actions.launch(it, null) } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues()
            when {
                results.isEmpty() && searching -> EmptySearch(model.query)
                style == DrawerStyle.GRID -> AppGrid(results, gridState, bottom, actions, model)
                else -> AppList(results, listState, bottom, actions, model, showSections = !searching)
            }
            if (!searching && sections.size > 1 && results.size > 20) {
                val scope = rememberCoroutineScope()
                FastScroller(
                    sections = sections,
                    touch = touch,
                    onSection = { index ->
                        scope.launch {
                            if (style == DrawerStyle.GRID) gridState.scrollToItem(index) else listState.scrollToItem(index)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = bottom.calculateBottomPadding()),
                )
            }
        }
    }

    if (showHidden) {
        HiddenAppsDialog(model, onDismiss = { showHidden = false })
    }
}

@Composable
private fun SearchBar(
    model: DrawerModel,
    style: DrawerStyle,
    onStyle: (DrawerStyle) -> Unit,
    onShowHidden: () -> Unit,
    onGo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val searchLabel = stringResource(R.string.search_apps)
    Row(
        Modifier
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceContainerHighest),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 12.dp).size(20.dp))
            BasicTextField(
                value = model.query,
                onValueChange = { model.query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = searchLabel },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (model.query.isEmpty()) {
                            Text(searchLabel, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            if (model.query.isNotEmpty()) {
                IconButton(onClick = { model.query = "" }) {
                    Icon(Glyphs.Close, contentDescription = stringResource(R.string.clear_search), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Glyphs.More, contentDescription = stringResource(R.string.drawer_options), tint = colors.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                val next = if (style == DrawerStyle.GRID) DrawerStyle.LIST else DrawerStyle.GRID
                DropdownMenuItem(
                    text = { Text(stringResource(if (next == DrawerStyle.LIST) R.string.list_view else R.string.grid_view)) },
                    onClick = { menu = false; onStyle(next) },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.hidden_apps)) }, onClick = { menu = false; onShowHidden() })
            }
        }
    }
}

/** Holds an item's layout coordinates without causing recomposition when they change. */
private class Coords {
    var value: LayoutCoordinates? = null

    fun bounds(): Rect? {
        val c = value?.takeIf { it.isAttached } ?: return null
        val b = c.boundsInWindow()
        return Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
    }
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    state: LazyGridState,
    bottom: PaddingValues,
    actions: DrawerActions,
    model: DrawerModel,
) {
    val optionsLabel = stringResource(R.string.action_options)
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        contentPadding = PaddingValues(start = 8.dp, end = 24.dp, top = 4.dp, bottom = bottom.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.key.toString() }, contentType = { 0 }) { app ->
            AppItem(app, actions, model) { coords, onClick, onLongClick ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onPlaced { coords.value = it }
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = optionsLabel,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(app.image, contentDescription = null, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppInfo>,
    state: LazyListState,
    bottom: PaddingValues,
    actions: DrawerActions,
    model: DrawerModel,
    showSections: Boolean,
) {
    val optionsLabel = stringResource(R.string.action_options)
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 8.dp, end = 28.dp, top = 4.dp, bottom = bottom.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps.size, key = { apps[it].key.toString() }, contentType = { 0 }) { i ->
            val app = apps[i]
            val firstOfSection = showSections && (i == 0 || apps[i - 1].section != app.section)
            AppItem(app, actions, model) { coords, onClick, onLongClick ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onPlaced { coords.value = it }
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(role = Role.Button, onLongClickLabel = optionsLabel, onLongClick = onLongClick, onClick = onClick)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (firstOfSection) app.section else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp),
                    )
                    Image(app.image, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Shared click, long-press menu and launch-bounds handling for grid and list items. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppItem(
    app: AppInfo,
    actions: DrawerActions,
    model: DrawerModel,
    content: @Composable (coords: Coords, onClick: () -> Unit, onLongClick: () -> Unit) -> Unit,
) {
    val coords = remember { Coords() }
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        content(coords, { actions.launch(app, coords.bounds()) }) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menu = true
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_to_home)) }, onClick = { menu = false; actions.addToHome(app) })
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_app_info)) }, onClick = { menu = false; actions.appInfo(app, coords.bounds()) })
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_hide)) }, onClick = { menu = false; model.setHidden(app, true) })
            if (!app.isSystem) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_uninstall)) }, onClick = { menu = false; actions.uninstall(app) })
            }
        }
    }
}

@Composable
private fun EmptySearch(query: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            stringResource(R.string.no_search_results, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}

/**
 * A-Z rail. Touching or dragging over it jumps the list to that letter, with a tick per letter
 * and a bubble beside the finger. It blocks the drawer-close gesture while held.
 */
@Composable
private fun FastScroller(
    sections: List<Pair<String, Int>>,
    touch: DragLayer.DrawerTouchState,
    onSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf<String?>(null) }
    var activeY by remember { mutableFloatStateOf(0f) }
    val indexLabel = stringResource(R.string.alphabet_index)
    val colors = MaterialTheme.colorScheme

    Box(modifier.fillMaxHeight().width(28.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = indexLabel }
                .pointerInput(sections) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touch.blockDrag = true
                        var last = -1
                        fun select(y: Float) {
                            val i = (y / size.height * sections.size).toInt().coerceIn(0, sections.size - 1)
                            activeY = y
                            if (i != last) {
                                last = i
                                active = sections[i].first
                                onSection(sections[i].second)
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                        select(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            select(change.position.y)
                        }
                        touch.blockDrag = false
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for ((letter, _) in sections) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (letter == active) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        active?.let { letter ->
            Box(
                Modifier
                    .offset { IntOffset((-64).dp.roundToPx(), (activeY - 24.dp.toPx()).roundToInt()) }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun HiddenAppsDialog(model: DrawerModel, onDismiss: () -> Unit) {
    val hidden by model.hidden.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.hidden_apps)) },
        text = {
            if (hidden.isEmpty()) {
                Text(stringResource(R.string.hidden_apps_empty))
            } else {
                LazyColumn {
                    items(hidden, key = { it.key.toString() }) { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.image, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { model.setHidden(app, false) }) { Text(stringResource(R.string.show)) }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}
