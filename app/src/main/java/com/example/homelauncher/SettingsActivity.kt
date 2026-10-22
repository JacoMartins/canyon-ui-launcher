package com.example.homelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.homelauncher.data.IconSize
import com.example.homelauncher.data.LauncherSettings
import com.example.homelauncher.data.LayoutMode
import com.example.homelauncher.data.SettingsStore
import com.example.homelauncher.ui.drawer.DrawerModel
import com.example.homelauncher.ui.drawer.HiddenAppsDialog
import com.example.homelauncher.ui.theme.HomeTheme
import kotlinx.coroutines.launch

/** Home screen settings. Grows in Stage 5; Stage 1.2 has layout mode, grid and hidden apps. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp
        val hiddenModel = DrawerModel(app.apps, app.settings, lifecycleScope)
        setContent {
            HomeTheme {
                val settings by app.settings.settings.collectAsState(initial = null)
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    settings?.let { SettingsScreen(it, app.settings, hiddenModel) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(s: LauncherSettings, store: SettingsStore, hiddenModel: DrawerModel) {
    val scope = rememberCoroutineScope()
    var showHidden by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp),
        )

        Card {
            SectionTitle(stringResource(R.string.layout_mode))
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
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.layout_separate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Card {
            SectionTitle(stringResource(R.string.home_grid))
            Text(stringResource(R.string.grid_columns), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in SettingsStore.MIN_COLUMNS..SettingsStore.MAX_COLUMNS) {
                    FilterChip(
                        selected = s.columns == c,
                        onClick = { scope.launch { store.setGrid(s.layoutMode, c, s.rows) } },
                        label = { Text("$c") },
                    )
                }
            }
            Text(stringResource(R.string.grid_rows), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in SettingsStore.MIN_ROWS..SettingsStore.MAX_ROWS) {
                    FilterChip(
                        selected = s.rows == r,
                        onClick = { scope.launch { store.setGrid(s.layoutMode, s.columns, r) } },
                        label = { Text("$r") },
                    )
                }
            }
        }

        Card {
            SectionTitle(stringResource(R.string.icon_size))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in IconSize.entries) {
                    val label = when (size) {
                        IconSize.SMALL -> R.string.icon_size_small
                        IconSize.MEDIUM -> R.string.icon_size_medium
                        IconSize.LARGE -> R.string.icon_size_large
                    }
                    FilterChip(
                        selected = s.grid.iconSize == size,
                        onClick = { scope.launch { store.setIconSize(s.layoutMode, size) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_labels), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.show_labels_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = s.grid.showLabels,
                    onCheckedChange = { scope.launch { store.setShowLabels(s.layoutMode, it) } },
                )
            }
        }

        Card(onClick = { showHidden = true }) {
            Text(stringResource(R.string.hidden_apps), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.hidden_apps_per_layout), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pluralHidden(s.hiddenApps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showHidden) HiddenAppsDialog(hiddenModel, onDismiss = { showHidden = false })
}

@Composable
private fun pluralHidden(n: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.hidden_count, n, n)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}
