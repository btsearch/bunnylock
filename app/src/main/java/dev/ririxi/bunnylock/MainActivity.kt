package dev.ririxi.bunnylock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ririxi.bunnylock.data.AppLanguage
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.ui.BunnyApp
import dev.ririxi.bunnylock.ui.MainViewModel
import dev.ririxi.bunnylock.ui.theme.BunnyTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.loadSims()
            vm.refresh()
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    // Android re-applies the first style on some configuration changes, so it has to read the saved choice itself.
    private val barStyle =
        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resources ->
            vm.state.value.themeMode.isDark(resources.configuration.isNightModeActive)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(barStyle, barStyle)
        if (savedInstanceState === null) {
            val wanted =
                mutableListOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(wanted.toTypedArray())
            handleNotificationAction(intent)
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val darkTheme = state.themeMode.isDark(isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(barStyle, barStyle)
                onDispose {}
            }
            BunnyTheme(dynamicColor = state.dynamicColor, darkTheme = darkTheme, pureBlack = state.pureBlack) {
                BunnyApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationAction(intent)
    }

    private fun handleNotificationAction(intent: Intent?) {
        if (intent?.action == LockNotifier.ACTION_UNLOCK_ALL) vm.unlockFromNotification()
    }
}
