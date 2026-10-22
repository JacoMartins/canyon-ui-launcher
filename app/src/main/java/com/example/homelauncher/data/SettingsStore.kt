package com.example.homelauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** "Home and Apps screens" (with a drawer) or "Home screen only" (every app on home). */
enum class LayoutMode(val storageKey: String) {
    HOME_AND_DRAWER("home_drawer"),
    HOME_ONLY("home_only"),
}

/**
 * Icon size as a share of the column width, with an upper bound in dp. Medium matches One UI's
 * 4x6 home screen, where an icon fills about 71% of its column.
 */
enum class IconSize(val cellFraction: Float, val maxDp: Int) {
    SMALL(0.62f, 64),
    MEDIUM(0.72f, 72),
    LARGE(0.82f, 80),
}

/** Grid, dock and icon size of one layout mode. Each mode keeps its own. */
data class GridSpec(
    val columns: Int = 4,
    val rows: Int = 6,
    val dockSlots: Int = 4,
    val iconSize: IconSize = IconSize.MEDIUM,
    /** Labels under home and dock icons. The Apps screen always shows them. */
    val showLabels: Boolean = false,
)

data class LauncherSettings(
    val layoutMode: LayoutMode = LayoutMode.HOME_AND_DRAWER,
    val grids: Map<LayoutMode, GridSpec> = LayoutMode.entries.associateWith { GridSpec() },
    val hiddenByMode: Map<LayoutMode, Set<String>> = LayoutMode.entries.associateWith { emptySet() },
    val firstRunDone: Boolean = false,
    val drawerHintSeen: Boolean = false,
) {
    /** Grid of the active mode. */
    val grid: GridSpec get() = grids.getValue(layoutMode)
    val columns get() = grid.columns
    val rows get() = grid.rows
    val dockSlots get() = grid.dockSlots
    val drawerEnabled get() = layoutMode == LayoutMode.HOME_AND_DRAWER

    /** Hidden apps of the active mode. */
    val hiddenApps: Set<String> get() = hiddenByMode.getValue(layoutMode)
}

/**
 * User settings in DataStore. Layout-specific values (grid, dock) are stored per [LayoutMode]
 * under the mode's prefix, so switching modes keeps each mode's own setup.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        /** Stage 1 kept one hidden list for everything; it seeds both modes' lists. */
        val LEGACY_HIDDEN = stringSetPreferencesKey("hidden_apps")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val DRAWER_HINT_SEEN = booleanPreferencesKey("drawer_hint_seen")

        fun columns(m: LayoutMode) = intPreferencesKey("${m.storageKey}.grid_columns")
        fun rows(m: LayoutMode) = intPreferencesKey("${m.storageKey}.grid_rows")
        fun dock(m: LayoutMode) = intPreferencesKey("${m.storageKey}.dock_slots")
        fun iconSize(m: LayoutMode) = stringPreferencesKey("${m.storageKey}.icon_size")
        fun showLabels(m: LayoutMode) = booleanPreferencesKey("${m.storageKey}.show_labels")
        fun hidden(m: LayoutMode) = stringSetPreferencesKey("${m.storageKey}.hidden_apps")
    }

    private fun grid(p: Preferences, m: LayoutMode): GridSpec {
        val d = GridSpec()
        return GridSpec(
            columns = (p[Keys.columns(m)] ?: d.columns).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            rows = (p[Keys.rows(m)] ?: d.rows).coerceIn(MIN_ROWS, MAX_ROWS),
            dockSlots = (p[Keys.dock(m)] ?: d.dockSlots).coerceIn(4, 5),
            iconSize = p[Keys.iconSize(m)]?.let { v -> IconSize.entries.firstOrNull { it.name == v } } ?: d.iconSize,
            showLabels = p[Keys.showLabels(m)] ?: d.showLabels,
        )
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        val d = LauncherSettings()
        LauncherSettings(
            layoutMode = p[Keys.LAYOUT_MODE]?.let { v -> LayoutMode.entries.firstOrNull { it.name == v } } ?: d.layoutMode,
            grids = LayoutMode.entries.associateWith { grid(p, it) },
            hiddenByMode = LayoutMode.entries.associateWith { p[Keys.hidden(it)] ?: p[Keys.LEGACY_HIDDEN] ?: emptySet() },
            firstRunDone = p[Keys.FIRST_RUN_DONE] ?: false,
            drawerHintSeen = p[Keys.DRAWER_HINT_SEEN] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun setLayoutMode(mode: LayoutMode) {
        context.dataStore.edit { it[Keys.LAYOUT_MODE] = mode.name }
    }

    suspend fun setGrid(mode: LayoutMode, columns: Int, rows: Int) {
        context.dataStore.edit {
            it[Keys.columns(mode)] = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            it[Keys.rows(mode)] = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        }
    }

    suspend fun setIconSize(mode: LayoutMode, size: IconSize) {
        context.dataStore.edit { it[Keys.iconSize(mode)] = size.name }
    }

    suspend fun setShowLabels(mode: LayoutMode, show: Boolean) {
        context.dataStore.edit { it[Keys.showLabels(mode)] = show }
    }

    /** Hides or shows an app in the active layout mode only. */
    suspend fun setHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { p ->
            val mode = p[Keys.LAYOUT_MODE]?.let { v -> LayoutMode.entries.firstOrNull { it.name == v } } ?: LayoutMode.HOME_AND_DRAWER
            val set = (p[Keys.hidden(mode)] ?: p[Keys.LEGACY_HIDDEN] ?: emptySet()).toMutableSet()
            if (hidden) set += key else set -= key
            p[Keys.hidden(mode)] = set
        }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.FIRST_RUN_DONE] = true }
    }

    suspend fun setDrawerHintSeen() {
        context.dataStore.edit { it[Keys.DRAWER_HINT_SEEN] = true }
    }

    companion object {
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = 6
        const val MIN_ROWS = 5
        const val MAX_ROWS = 7
    }
}
