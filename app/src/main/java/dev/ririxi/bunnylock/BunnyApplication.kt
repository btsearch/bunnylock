package dev.ririxi.bunnylock

import android.app.Application
import android.content.res.Configuration
import dev.ririxi.bunnylock.data.AppLanguage

class BunnyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.apply(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.apply(this)
    }
}
