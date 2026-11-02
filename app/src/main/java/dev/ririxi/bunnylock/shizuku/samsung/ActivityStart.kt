package dev.ririxi.bunnylock.shizuku.samsung

import android.content.Intent
import dev.ririxi.bunnylock.shizuku.HiddenApi
import dev.ririxi.bunnylock.shizuku.invokeUnwrapped

internal object ActivityStart {
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val MENU_PERMISSION = "com.sec.android.app.hiddenmenu.permission.KEYSTRING"

    private const val START_SUCCESS = 0
    private const val LAST_START_SUCCESS_CODE = 99
    private const val START_PERMISSION_DENIED = -94
    private const val START_CLASS_NOT_FOUND = -92
    private const val START_INTENT_NOT_RESOLVED = -91

    fun open(intent: Intent) {
        val activityManager = HiddenApi.activityManager()
        try {
            start(activityManager, intent)
        } catch (denied: SecurityException) {
            if (!grantMenuPermissionToShell()) throw denied
            start(activityManager, intent)
        }
    }

    private fun start(
        activityManager: Any,
        intent: Intent,
    ) {
        val startActivity = HiddenApi.method("android.app.IActivityManager", "startActivityWithFeature", 11)
        // Shizuku with ADB access runs as the shell user, and the activity service checks the calling package against that uid.
        // The nulls are caller, feature id, resolved type, result target and who, profiler info and options.
        val result =
            startActivity.invokeUnwrapped(activityManager, null, SHELL_PACKAGE, null, intent, null, null, null, 0, 0, null, null) as Int
        if (result in START_SUCCESS..LAST_START_SUCCESS_CODE) return
        throw when (result) {
            START_PERMISSION_DENIED -> SecurityException("$PERMISSION_DENIAL: start returned $result")
            START_CLASS_NOT_FOUND -> IllegalStateException("The activity class does not exist ($result)")
            START_INTENT_NOT_RESOLVED -> IllegalStateException("Nothing to start for the intent ($result)")
            else -> IllegalStateException("Start returned $result")
        }
    }

    private fun grantMenuPermissionToShell(): Boolean =
        runCatching {
            val grantRuntimePermission = HiddenApi.method("android.content.pm.IPackageManager", "grantRuntimePermission", 3)
            grantRuntimePermission.invokeUnwrapped(HiddenApi.packageManager(), SHELL_PACKAGE, MENU_PERMISSION, 0)
        }.isSuccess
}
