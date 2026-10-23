package io.github.jacomartins.canyonui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.size
import io.github.jacomartins.canyonui.data.IconShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.Canvas
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import io.github.jacomartins.canyonui.ui.design.Spacing
import io.github.jacomartins.canyonui.ui.design.isDarkSurface
import io.github.jacomartins.canyonui.ui.design.SectionHeader
import io.github.jacomartins.canyonui.ui.design.SettingsSection
import io.github.jacomartins.canyonui.ui.design.settingsPageColor
import androidx.lifecycle.lifecycleScope
import io.github.jacomartins.canyonui.data.AppInfo
import io.github.jacomartins.canyonui.data.IconSize
import io.github.jacomartins.canyonui.data.HomeRepository
import io.github.jacomartins.canyonui.data.LauncherSettings
import io.github.jacomartins.canyonui.data.LayoutMode
import io.github.jacomartins.canyonui.data.SettingsStore
import io.github.jacomartins.canyonui.ui.drawer.DrawerModel
import io.github.jacomartins.canyonui.ui.drawer.HiddenAppsDialog
import io.github.jacomartins.canyonui.ui.design.ChoiceButton
import io.github.jacomartins.canyonui.ui.design.PrimaryButton
import io.github.jacomartins.canyonui.ui.design.SecondaryButton
import io.github.jacomartins.canyonui.ui.design.WallpaperBackdrop
import io.github.jacomartins.canyonui.data.RenderMode
import io.github.jacomartins.canyonui.ui.settings.IconPackPicker
import io.github.jacomartins.canyonui.ui.settings.AppTargetPicker
import io.github.jacomartins.canyonui.ui.settings.SEARCH_ACTIONS
import io.github.jacomartins.canyonui.ui.theme.HomeTheme
import kotlinx.coroutines.launch

/** Home screen settings. Grows in Stage 5; Stage 1.2 has layout mode, grid and hidden apps. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp
        val hiddenModel = DrawerModel(app.apps, app.settings, lifecycleScope)
        setContent {
            val settings by app.settings.settings.collectAsState(initial = null)
            HomeTheme(phonePalette = settings?.phonePalette == true) {
                Surface(Modifier.fillMaxSize(), color = settingsPageColor()) {
                    val apps by app.apps.apps.collectAsState()
                    settings?.let { SettingsScreen(it, app.settings, hiddenModel, apps.orEmpty(), app.home) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(s: LauncherSettings, store: SettingsStore, hiddenModel: DrawerModel, apps: List<AppInfo>, home: HomeRepository) {
    val scope = rememberCoroutineScope()
    var showHidden by remember { mutableStateOf(false) }
    var showPacks by remember { mutableStateOf(false) }
    var showSwipeUp by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Page title (h1): bold, lined up with the text inside the containers.
        Text(
            stringResource(R.string.home_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Spacing.mdLg, top = Spacing.lg, bottom = Spacing.md),
        )
        // Radio buttons and switches without Material's extra 48dp touch padding, so they line
        // up with the text; the whole row is the touch target.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {

        SettingsSection(title = stringResource(R.string.layout_mode)) {
            Column(Modifier.selectableGroup()) {
                for (mode in LayoutMode.entries) {
                    val (title, summary) = when (mode) {
                        LayoutMode.HOME_AND_DRAWER -> R.string.layout_home_and_apps to R.string.layout_home_and_apps_summary
                        LayoutMode.HOME_ONLY -> R.string.layout_home_only to R.string.layout_home_only_summary
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.layoutMode == mode, role = Role.RadioButton) {
                                scope.launch { store.setLayoutMode(mode) }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.layoutMode == mode, onClick = null)
                        Column(Modifier.padding(start = Spacing.smMd)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (s.layoutMode == LayoutMode.HOME_ONLY) {
                SwitchRow(
                    title = stringResource(R.string.auto_categories),
                    checked = s.autoCategories,
                    onChange = { on ->
                        scope.launch {
                            // Folders first, then the switch, so new apps never see a half-built layout.
                            if (on) {
                                // The home page gets a Google search bar when there is room for one.
                                if (home.organizeByCategory()) store.setPendingSearchWidget(true)
                            } else {
                                home.unlockCategories()
                            }
                            store.setAutoCategories(on)
                        }
                    },
                )
                Text(stringResource(R.string.auto_categories_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The Apps screen is off in this mode, so its swipe is free for an app of choice.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showSwipeUp = true }
                        .padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.swipe_up), style = MaterialTheme.typography.titleMedium)
                    val target = s.swipeUpTarget
                    val current = target?.let { t ->
                        val pkg = t.substringAfter('|').substringBefore('/')
                        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    } ?: stringResource(R.string.swipe_up_none)
                    Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.swipe_up_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.layout_separate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SettingsSection(title = stringResource(R.string.home_grid)) {
            Text(stringResource(R.string.grid_columns), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in SettingsStore.MIN_COLUMNS..SettingsStore.MAX_COLUMNS) {
                    ChoiceButton("$c", s.columns == c, { scope.launch { store.setGrid(s.layoutMode, c, s.rows) } }, Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.grid_rows), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in SettingsStore.MIN_ROWS..SettingsStore.MAX_ROWS) {
                    ChoiceButton("$r", s.rows == r, { scope.launch { store.setGrid(s.layoutMode, s.columns, r) } }, Modifier.weight(1f))
                }
            }
        }

        SettingsSection(onClick = { showPacks = true }) {
            Text(stringResource(R.string.icon_pack), style = MaterialTheme.typography.titleMedium)
            Text(
                s.iconPack?.let { pkg ->
                    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
                        .getOrDefault(pkg)
                } ?: stringResource(R.string.icon_pack_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(title = stringResource(R.string.icon_size)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in IconSize.entries) {
                    val label = when (size) {
                        IconSize.SMALL -> R.string.icon_size_small
                        IconSize.MEDIUM -> R.string.icon_size_medium
                        IconSize.LARGE -> R.string.icon_size_large
                    }
                    ChoiceButton(stringResource(label), s.grid.iconSize == size, { scope.launch { store.setIconSize(s.layoutMode, size) } }, Modifier.weight(1f))
                }
            }
        }

        SettingsSection {
            SwitchRow(
                title = stringResource(R.string.phone_palette),
                checked = s.phonePalette,
                onChange = { scope.launch { store.setPhonePalette(it) } },
            )
            Text(stringResource(R.string.phone_palette_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = stringResource(R.string.render_mode)) {
            RenderModeChoice(s.renderMode, onSelect = { scope.launch { store.setRenderMode(it) } })
        }

        SettingsSection(title = stringResource(R.string.icon_shape)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (shape in IconShape.entries) {
                    ShapeChoice(shape, s.iconStyle.shape == shape, Modifier.weight(1f)) {
                        scope.launch { store.setIconShape(shape) }
                    }
                }
            }
            Text(stringResource(R.string.icon_shape_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Build.VERSION.SDK_INT >= 33) {
                SwitchRow(
                    title = stringResource(R.string.themed_icons),
                    checked = s.iconStyle.themed,
                    onChange = { scope.launch { store.setThemedIcons(it) } },
                )
                Text(stringResource(R.string.themed_icons_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.iconPack != null) {
                SwitchRow(
                    title = stringResource(R.string.pack_style),
                    checked = s.iconStyle.packStyle,
                    onChange = { scope.launch { store.setPackStyle(it) } },
                )
                Text(stringResource(R.string.pack_style_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsSection {
            SwitchRow(
                title = stringResource(R.string.show_home_labels),
                checked = s.grid.showHomeLabels,
                onChange = { scope.launch { store.setShowHomeLabels(s.layoutMode, it) } },
            )
            SwitchRow(
                title = stringResource(R.string.show_dock_labels),
                checked = s.grid.showDockLabels,
                onChange = { scope.launch { store.setShowDockLabels(s.layoutMode, it) } },
            )
            Text(stringResource(R.string.labels_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection {
            SwitchRow(
                title = stringResource(R.string.resize_any_widget),
                checked = s.resizeAnyWidget,
                onChange = { scope.launch { store.setResizeAnyWidget(it) } },
            )
            Text(stringResource(R.string.resize_any_widget_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow(
                title = stringResource(R.string.widget_border),
                checked = s.widgetBorder,
                onChange = { scope.launch { store.setWidgetBorder(it) } },
            )
            SwitchRow(
                title = stringResource(R.string.full_cell_widget_sizes),
                checked = s.fullCellWidgetSizes,
                onChange = { scope.launch { store.setFullCellWidgetSizes(it) } },
            )
            Text(stringResource(R.string.full_cell_widget_sizes_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(onClick = { showHidden = true }) {
            Text(stringResource(R.string.hidden_apps), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hidden_apps_per_layout), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pluralHidden(s.hiddenApps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(onClick = { showLicenses = true }) {
            Text(stringResource(R.string.licenses), style = MaterialTheme.typography.titleMedium)
        }
        }
    }
    if (showLicenses) {
        val text = remember { context.assets.open("licenses/phosphor-icons.txt").bufferedReader().use { it.readText() } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLicenses = false },
            confirmButton = { PrimaryButton(stringResource(R.string.done), { showLicenses = false }) },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Phosphor Icons", style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }
    if (showHidden) HiddenAppsDialog(hiddenModel, onDismiss = { showHidden = false })
    if (showSwipeUp) {
        AppTargetPicker(
            title = stringResource(R.string.swipe_up),
            noneLabel = stringResource(R.string.swipe_up_none),
            specialHeader = stringResource(R.string.swipe_up_search_apps),
            actions = SEARCH_ACTIONS,
            selected = s.swipeUpTarget,
            apps = apps,
            onSelect = { scope.launch { store.setSwipeUpTarget(it) }; showSwipeUp = false },
            onDismiss = { showSwipeUp = false },
        )
    }
    if (showPacks) {
        IconPackPicker(
            selected = s.iconPack,
            apps = apps,
            onSelect = { scope.launch { store.setIconPack(it) } },
            onDismiss = { showPacks = false },
        )
    }
}

/** Translucent, frosted or glass (one at a time), and the wallpaper photo the blur needs. */
@Composable
private fun RenderModeChoice(mode: RenderMode, onSelect: (RenderMode) -> Unit) {
    val context = LocalContext.current
    val hasCopy by WallpaperBackdrop.hasCopy.collectAsState()
    Column(Modifier.selectableGroup()) {
        for (m in RenderMode.entries) {
            val (title, summary) = when (m) {
                RenderMode.TRANSLUCENT -> R.string.render_translucent to R.string.render_translucent_summary
                RenderMode.FROSTED -> R.string.render_frosted to R.string.render_frosted_summary
                RenderMode.GLASS -> R.string.render_glass to R.string.render_glass_summary
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == m, role = Role.RadioButton) { onSelect(m) }
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = null)
                Column(Modifier.padding(start = Spacing.smMd)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (mode != RenderMode.TRANSLUCENT) {
        Text(
            stringResource(if (hasCopy) R.string.render_has_photo else R.string.render_needs_photo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val label = stringResource(R.string.render_pick_photo)
        val onPick = { context.startActivity(android.content.Intent(context, WallpaperActivity::class.java)) }
        if (hasCopy) SecondaryButton(label, onPick, icon = R.drawable.ph_wallpaper) else PrimaryButton(label, onPick, icon = R.drawable.ph_wallpaper)
    }
}

@Composable
private fun pluralHidden(n: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.hidden_count, n, n)

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A shape preview with its name; the selected one is filled with the primary colour. */
@Composable
private fun ShapeChoice(shape: IconShape, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(
        when (shape) {
            IconShape.SYSTEM -> R.string.shape_system
            IconShape.CIRCLE -> R.string.shape_circle
            IconShape.SQUIRCLE -> R.string.shape_squircle
            IconShape.ROUNDED -> R.string.shape_rounded
            IconShape.SQUARE -> R.string.shape_square
        },
    )
    val colors = MaterialTheme.colorScheme
    val fill = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(40.dp)) {
            val p = shape.path(size.minDimension)
            if (p != null) {
                drawPath(p.asComposePath(), fill)
            } else {
                // The phone's own mask.
                val probe = android.graphics.drawable.AdaptiveIconDrawable(0.toDrawable(), 0.toDrawable())
                probe.setBounds(0, 0, size.minDimension.toInt(), size.minDimension.toInt())
                drawPath(probe.iconMask.asComposePath(), fill)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.primary else colors.onSurfaceVariant, maxLines = 1)
    }
}

