package com.example.homelauncher.ui.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.homelauncher.data.AppInfo
import com.example.homelauncher.data.AppRepository
import com.example.homelauncher.data.IconSize
import com.example.homelauncher.data.SettingsStore
import com.example.homelauncher.model.AppSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drawer state: search query, filtered apps and hidden apps. */
class DrawerModel(
    apps: AppRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    /** Compose state so the text field updates synchronously while typing. */
    var query by mutableStateOf("")

    /** Bumped when a drag leaves the drawer, so open item menus close. */
    var dragEpoch by mutableIntStateOf(0)

    private val all = apps.apps.filterNotNull()
    private val hiddenKeys = settings.settings.map { it.hiddenApps }

    private val visible = combine(all, hiddenKeys) { list, hidden ->
        if (hidden.isEmpty()) list else list.filter { it.key.toString() !in hidden }
    }

    val results: StateFlow<List<AppInfo>> = combine(visible, snapshotFlow { query }) { list, q ->
        AppSearch.filter(list, q) { it.label }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val hidden: StateFlow<List<AppInfo>> = combine(all, hiddenKeys) { list, hidden ->
        list.filter { it.key.toString() in hidden }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The active layout's icon size; the Apps screen uses it too. */
    val iconSize: StateFlow<IconSize> = settings.settings.map { it.grid.iconSize }
        .stateIn(scope, SharingStarted.Eagerly, IconSize.MEDIUM)

    fun setHidden(app: AppInfo, hidden: Boolean) {
        scope.launch { settings.setHidden(app.key.toString(), hidden) }
    }
}
