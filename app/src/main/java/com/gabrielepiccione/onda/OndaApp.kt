package com.gabrielepiccione.onda

import android.app.Application
import com.gabrielepiccione.onda.data.AppRepository
import com.gabrielepiccione.onda.data.SettingsRepository
import com.gabrielepiccione.onda.data.WidgetManager

class OndaApp : Application() {

    lateinit var apps: AppRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var widgets: WidgetManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        apps = AppRepository(this)
        widgets = WidgetManager(this)
    }
}
