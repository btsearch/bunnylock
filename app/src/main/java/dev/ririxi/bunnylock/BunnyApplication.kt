package dev.ririxi.bunnylock

import android.app.Application
import android.content.res.Configuration
import dev.ririxi.bunnylock.data.AppLanguage
import org.lsposed.hiddenapibypass.HiddenApiBypass

class BunnyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The Shizuku activity launch reaches SDK-hidden services like IActivityManager, so the runtime wall is removed.
        HiddenApiBypass.setHiddenApiExemptions("L")
        AppLanguage.apply(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.apply(this)
    }
}
