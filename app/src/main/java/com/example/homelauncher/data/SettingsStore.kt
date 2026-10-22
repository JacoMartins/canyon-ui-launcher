package com.example.homelauncher.data

import android.content.Context
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

enum class DrawerStyle { GRID, LIST }

data class LauncherSettings(
    val columns: Int = 5,
    val rows: Int = 6,
    val dockSlots: Int = 4,
    val drawerStyle: DrawerStyle = DrawerStyle.GRID,
    val hiddenApps: Set<String> = emptySet(),
    val firstRunDone: Boolean = false,
    val drawerHintSeen: Boolean = false,
)

/** User settings in DataStore. Stage 1 exposes drawer style and hidden apps in the UI. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val COLUMNS = intPreferencesKey("grid_columns")
        val ROWS = intPreferencesKey("grid_rows")
        val DOCK_SLOTS = intPreferencesKey("dock_slots")
        val DRAWER_STYLE = stringPreferencesKey("drawer_style")
        val HIDDEN = stringSetPreferencesKey("hidden_apps")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val DRAWER_HINT_SEEN = booleanPreferencesKey("drawer_hint_seen")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        val d = LauncherSettings()
        LauncherSettings(
            columns = (p[Keys.COLUMNS] ?: d.columns).coerceIn(4, 6),
            rows = (p[Keys.ROWS] ?: d.rows).coerceIn(5, 7),
            dockSlots = (p[Keys.DOCK_SLOTS] ?: d.dockSlots).coerceIn(4, 5),
            drawerStyle = p[Keys.DRAWER_STYLE]?.let { runCatching { DrawerStyle.valueOf(it) }.getOrNull() } ?: d.drawerStyle,
            hiddenApps = p[Keys.HIDDEN] ?: d.hiddenApps,
            firstRunDone = p[Keys.FIRST_RUN_DONE] ?: false,
            drawerHintSeen = p[Keys.DRAWER_HINT_SEEN] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun setDrawerStyle(style: DrawerStyle) {
        context.dataStore.edit { it[Keys.DRAWER_STYLE] = style.name }
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { p ->
            val set = (p[Keys.HIDDEN] ?: emptySet()).toMutableSet()
            if (hidden) set += key else set -= key
            p[Keys.HIDDEN] = set
        }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.FIRST_RUN_DONE] = true }
    }

    suspend fun setDrawerHintSeen() {
        context.dataStore.edit { it[Keys.DRAWER_HINT_SEEN] = true }
    }
}
