package com.example.homelauncher.ui.drawer

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.homelauncher.R
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.ui.common.Glyphs
import com.example.homelauncher.ui.home.DragLayer
import kotlin.math.roundToInt

/** What the drawer asks the activity to do. */
interface DrawerActions {
    fun launch(app: AppInfo, bounds: Rect?)
    fun appInfo(app: AppInfo, bounds: Rect?)
    fun uninstall(app: AppInfo)
    fun addToHome(app: AppInfo)
    fun openSettings()

    /** Null when no speech recognizer is installed (the mic button is then hidden). */
    val voiceSearch: (() -> Unit)?
}

private const val COLUMNS = 4
private val SearchPillHeight = 56.dp

@Composable
fun AppDrawer(model: DrawerModel, touch: DragLayer.DrawerTouchState, actions: DrawerActions) {
    val results by model.results.collectAsState()
    var showHidden by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // The parent closes the drawer on a downward drag only when the grid is at its top.
    LaunchedEffect(Unit) {
        snapshotFlow { gridState.canScrollBackward }.collect { touch.contentAtTop = !it }
    }
    // A new query starts at the top.
    LaunchedEffect(Unit) {
        snapshotFlow { model.query }.collect { gridState.scrollToItem(0) }
    }

    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            TopBar(onShowHidden = { showHidden = true }, onSettings = actions::openSettings)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val bottom = bottomInsets.asPaddingValues().calculateBottomPadding() + SearchPillHeight + 24.dp
                if (results.isEmpty() && model.query.isNotBlank()) {
                    EmptySearch(model.query)
                } else {
                    AppGrid(results, gridState, bottom, actions, model)
                }
            }
        }
        SearchPill(
            model = model,
            voiceSearch = actions.voiceSearch,
            onGo = { results.firstOrNull()?.let { actions.launch(it, null) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(bottomInsets)
                .padding(bottom = 12.dp),
        )
    }

    if (showHidden) {
        HiddenAppsDialog(model, onDismiss = { showHidden = false })
    }
}

@Composable
private fun TopBar(onShowHidden: () -> Unit, onSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Glyphs.More, contentDescription = stringResource(R.string.drawer_options), tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.hidden_apps)) }, onClick = { menu = false; onShowHidden() })
                DropdownMenuItem(text = { Text(stringResource(R.string.home_settings)) }, onClick = { menu = false; onSettings() })
            }
        }
    }
}

/**
 * Floating search field at the bottom. Compact and centred while browsing; widens to full width
 * when focused or holding a query. Sits above the keyboard.
 */
@Composable
private fun SearchPill(
    model: DrawerModel,
    voiceSearch: (() -> Unit)?,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val expanded = focused || model.query.isNotEmpty()
    val widthFraction by animateFloatAsState(if (expanded) 1f else 0.66f, label = "searchWidth")
    val focus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.search)

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(widthFraction)
            .height(SearchPillHeight),
        shape = CircleShape,
        color = colors.surfaceContainerHighest,
        shadowElevation = 6.dp,
        onClick = { focus.requestFocus() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 12.dp).size(22.dp))
            BasicTextField(
                value = model.query,
                onValueChange = { model.query = it },
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .semantics { contentDescription = searchLabel },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (model.query.isEmpty()) {
                            Text(searchLabel, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            when {
                model.query.isNotEmpty() -> IconButton(onClick = { model.query = "" }) {
                    Icon(Glyphs.Close, contentDescription = stringResource(R.string.clear_search), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                voiceSearch != null -> IconButton(onClick = voiceSearch, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Glyphs.Mic, contentDescription = stringResource(R.string.voice_search), tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                else -> Spacer(Modifier.width(20.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    state: LazyGridState,
    bottom: androidx.compose.ui.unit.Dp,
    actions: DrawerActions,
    model: DrawerModel,
) {
    val optionsLabel = stringResource(R.string.action_options)
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.key.toString() }, contentType = { 0 }) { app ->
            val coords = remember { Coords() }
            var menu by remember { mutableStateOf(false) }
            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onPlaced { coords.value = it }
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = optionsLabel,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                            onClick = { actions.launch(app, coords.bounds()) },
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(app.image, contentDescription = null, modifier = Modifier.size(60.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
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

@Composable
fun HiddenAppsDialog(model: DrawerModel, onDismiss: () -> Unit) {
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
