package com.example.homelauncher

import android.app.Application
import android.content.res.Configuration
import com.example.homelauncher.data.AppRepository
import com.example.homelauncher.data.DefaultApps
import com.example.homelauncher.data.HomeRepository
import com.example.homelauncher.data.IconCache
import com.example.homelauncher.data.LauncherDatabase
import com.example.homelauncher.data.LayoutMode
import com.example.homelauncher.data.SettingsStore
import kotlinx.coroutines.MainScope

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
        val daos = LayoutMode.entries.associateWith { LauncherDatabase.create(this, it).dao() }
        settings = SettingsStore(this)
        apps = AppRepository(this, scope, IconCache(this))
        home = HomeRepository(daos, settings, apps, DefaultApps(this), scope)
        apps.start()
        home.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        apps.reload() // Labels follow the locale.
    }
}
