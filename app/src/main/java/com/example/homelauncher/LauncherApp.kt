package com.example.homelauncher

import android.app.Application
import android.content.res.Configuration
import com.example.homelauncher.data.AppRepository
import com.example.homelauncher.model.AppCategories
import com.example.homelauncher.data.DefaultApps
import com.example.homelauncher.data.HomeRepository
import com.example.homelauncher.data.IconCache
import com.example.homelauncher.data.LauncherDatabase
import com.example.homelauncher.data.LayoutMode
import com.example.homelauncher.data.SettingsStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Process-wide objects, created once (manual dependency injection). */
class LauncherApp : Application() {

    val scope = MainScope()

    lateinit var settings: SettingsStore
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var home: HomeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val dbs = LayoutMode.entries.associateWith { LauncherDatabase.create(this, it) }
        settings = SettingsStore(this)
        apps = AppRepository(this, scope, IconCache(this))
        home = HomeRepository(dbs, settings, apps, DefaultApps(this), scope, ::categoryTitle)
        apps.start()
        home.start()
        scope.launch {
            settings.settings.map { it.iconPack }.distinctUntilChanged().collect { apps.setIconPack(it) }
        }
        scope.launch {
            settings.settings.map { it.iconStyle to it.iconOverrides }.distinctUntilChanged().collect { (style, overrides) ->
                apps.setIconStyle(style, overrides)
            }
        }
        // Dynamic clock icons move once a minute.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = apps.timeTick()
            },
            IntentFilter(Intent.ACTION_TIME_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val dateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = apps.dateChanged()
            },
            dateFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun categoryTitle(c: AppCategories.Category): String = getString(
        when (c) {
            AppCategories.Category.COMMUNICATION -> R.string.category_communication
            AppCategories.Category.ENTERTAINMENT -> R.string.category_entertainment
            AppCategories.Category.GAMES -> R.string.category_games
            AppCategories.Category.CREATION -> R.string.category_creation
            AppCategories.Category.PRODUCTIVITY -> R.string.category_productivity
            AppCategories.Category.EDUCATION_AI -> R.string.category_education_ai
            AppCategories.Category.TRIP -> R.string.category_trip
            AppCategories.Category.WELLNESS -> R.string.category_wellness
            AppCategories.Category.FINANCE -> R.string.category_finance
            AppCategories.Category.SHOPPING -> R.string.category_shopping
            AppCategories.Category.NEWS -> R.string.category_news
            AppCategories.Category.TOOLS -> R.string.category_tools
        },
    )

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        apps.onConfigurationChanged() // Labels follow the locale; themed icons the palette.
    }
}
