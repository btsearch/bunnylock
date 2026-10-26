package dev.ririxi.bunnylock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
            BunnyTheme(dynamicColor = state.dynamicColor) {
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
