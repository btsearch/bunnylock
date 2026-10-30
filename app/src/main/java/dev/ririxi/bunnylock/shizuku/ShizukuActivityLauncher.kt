package dev.ririxi.bunnylock.shizuku

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import dev.ririxi.bunnylock.data.RootException
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import dev.ririxi.bunnylock.modem.R as ModemR
import dev.ririxi.bunnylock.modem.Text
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import kotlinx.coroutines.CancellationException

/**
 * Opens Samsung's band menu however the phone allows it. Shizuku speaks for the app on the activity service itself,
 * Samsung's telecom server gets the intent by broadcast when the front door is shut, and the assist is pointed at the
 * menu and triggered when even the broadcast is. The root helper stays with `am start`, which only root's uid may use.
 */
object ShizukuActivityLauncher {
    // One UI 6+ first, then the older standalone package.
    private val SAMSUNG_MENUS =
        listOf(
            "com.samsung.android.app.telephonyui" to "com.samsung.android.app.telephonyui.hiddennetworksetting.MainActivity",
            "com.samsung.hiddennetworksetting" to "com.samsung.hiddennetworksetting.MainActivity",
        )

    // The start results are SDK-hidden, and binders count its fatal errors from -100 up: among them Samsung's
    // permission gate and the activity class the menu's older home has already lost.
    private const val START_SUCCESS = 0
    private const val START_PERMISSION_DENIED = -94

    /**
     * Samsung wants every starter of the band menu to hold this. The helper under root passes any check it likes, and
     * Shizuku's shell only has the permission once the package service hands it over — which it does when asked, and
     * doesn't when the permission belongs to Samsung's own signers.
     */
    private const val HIDDEN_MENU_PERMISSION = "com.sec.android.app.hiddenmenu.permission.KEYSTRING"
    private const val SHELL_PACKAGE = "com.android.shell"

    /** Samsung's free hand: a broadcast its telecom server accepts, which starts whatever intent the extra carries. */
    private const val TELECOM_ROUTE_ACTION = "com.samsung.server.telecom.USER_SELECT_WIFI_SERVICE_CALL"
    private const val TELECOM_ROUTE_INTENT_EXTRA = "extra_call_intent"

    /** The assist funnel reads its target from this setting, and fires when the keyboard's Assist key arrives. */
    private const val ASSISTANT_SETTING = "assistant"
    private const val ASSIST_KEYCODE = 219

    /** Opens the Samsung band menu where it lives these days, and reports failures as a [RootException]. */
    fun openSamsungMenu(context: Context) {
        val failures = ArrayList<String>()
        var denied: String? = null

        // Straight over the activity service, with Shizuku speaking for the app. Samsung answers the start with the
        // permission nobody else has, so then the package service is asked whether the shell can be granted it.
        val activityManager = shizukuActivityManager()
        val callingPackage = context.shellCallingPackage()
        for ((pkg, activity) in SAMSUNG_MENUS) {
            val wanted = "$pkg/$activity"
            val intent = Intent().setClassName(pkg, activity)
            val first = binderStart(activityManager, intent, callingPackage) ?: return
            if (first.isDenied) {
                denied = denied ?: "through Shizuku, $wanted: ${first.why}"
                // Ask for the permission the shell lacks, and retry the same start when it came through.
                if (grantToShell(HIDDEN_MENU_PERMISSION)) {
                    val again = binderStart(activityManager, intent, callingPackage) ?: return
                    if (!again.isDenied) failures += "through Shizuku, $wanted: ${again.why}"
                }
            } else {
                failures += "through Shizuku, $wanted: ${first.why}"
            }
        }

        // Then Samsung's telecom server, which starts the broadcast's payload with its own privileges, no root asked.
        // Neither route can confirm the menu opened, but a sent broadcast is as good as Samsung allows.
        for ((pkg, activity) in SAMSUNG_MENUS) {
            try {
                context.sendBroadcast(
                    Intent(TELECOM_ROUTE_ACTION).putExtra(TELECOM_ROUTE_INTENT_EXTRA, Intent().setClassName(pkg, activity)),
                )
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                record(e, failures, { denied = denied ?: it }, "through Samsung's telecom route, $pkg/$activity")
            }
        }

        // Last, Samsung's assist: the framework's own funnel starts the assistant from its side of the table, where
        // the band menu is nobody's business to refuse. The setting holds the menu while the assist asks for it,
        // and goes back once the start is under way.
        for ((pkg, activity) in SAMSUNG_MENUS) {
            try {
                assistStart(context, pkg, activity)
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                record(e, failures, { denied = denied ?: it }, "through Samsung's assist, $pkg/$activity")
            }
        }

        // Samsung's own software refuses every outside request, so a denial carries the word, and the rest are details.
        val details = ((denied?.let { listOf(it) } ?: emptyList()) + failures).joinToString(" / ")
        if (denied !== null) {
            throw RootException(context, Text(ModemR.string.aosp_with_details, Text(ModemR.string.sys_samsung_menu_denied), details))
        }
        if (details.isEmpty()) throw RootException(context, Text(ModemR.string.sys_samsung_menu_missing))
        throw RootException(
            context,
            Text(ModemR.string.aosp_with_details, Text(ModemR.string.sys_samsung_menu_missing), details),
        )
    }

    private val String.isDenied get() = "Permission Denial" in this

    private fun record(
        e: Exception,
        failures: ArrayList<String>,
        onDenied: (String) -> Unit,
        route: String,
    ) {
        val why = failureOf(e)
        if (why.isDenied) onDenied("$route: $why") else failures += "$route: $why"
    }

    private class Failure(
        val why: String,
    ) {
        val isDenied get() = "Permission Denial" in why
    }

    /** The start over Shizuku's binder, as a failure sentence, or null when the menu is up. */
    private fun binderStart(
        activityManager: Any,
        intent: Intent,
        callingPackage: String?,
    ): Failure? =
        try {
            when (val result = reflectedStart(activityManager, intent, callingPackage)) {
                in START_SUCCESS..99 -> null
                START_PERMISSION_DENIED -> Failure("Permission Denial: start returned ${startResultName(result)}")
                else -> Failure("start returned ${startResultName(result)}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Failure(failureOf(e))
        }

    /** The start results' names, for what the app reports to users. */
    private fun startResultName(result: Int): String =
        when (result) {
            -100 -> "$result, voice session hidden"
            -99 -> "$result, voice session wasn't the requester"
            -98 -> "$result, not the current user's activity"
            -97 -> "$result, outside voice's reach"
            -96 -> "$result, the start gave up"
            -95 -> "$result, not an activity"
            -94 -> "$result, permission denied"
            -93 -> "$result, result forwarding refused"
            -92 -> "$result, activity class missing"
            -91 -> "$result, nothing to start"
            -90 -> "$result, assistant session hidden"
            -89 -> "$result, assistant session wasn't the requester"
            else -> result.toString()
        }

    /** Refusals cross the binder wrapped in reflection's carry: unwrap them and keep the platform's own words. */
    private fun failureOf(e: Exception): String {
        val under =
            when (e) {
                is InvocationTargetException -> e.cause ?: e
                is UndeclaredThrowableException -> (e.undeclaredThrowable as? Exception) ?: e
                else -> e
            }
        return under.message?.takeIf { it.isNotBlank() } ?: under.javaClass.simpleName
    }

    /**
     * IActivityManager is SDK-hidden and there is no stub of it at compile time, so reaching the interface lives on
     * reflection (the static call behind [JavaBridge.iActivityManager]), which the hidden API exemptions BunnyApplication
     * sets keep open.
     */
    private fun shizukuActivityManager(): Any =
        JavaBridge.iActivityManager(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))

    /** The [startActivityWithFeature] call, on the books since Android 11 and stable since. */
    private fun reflectedStart(
        activityManager: Any,
        intent: Intent,
        callingPackage: String?,
    ): Int {
        val start =
            Class.forName("android.app.IActivityManager")
                .methods
                .firstOrNull { it.name == "startActivityWithFeature" && it.parameterCount == 11 }
                ?: throw NoSuchMethodException("android.app.IActivityManager.startActivityWithFeature")
        // caller, callingPackage, callingFeatureId, intent, resolvedType, resultTo, resultWho, requestCode, flags, profilerInfo, bOptions.
        return start.invoke(activityManager, null, callingPackage, null, intent, null, null, null, 0, 0, null, null) as Int
    }

    /** The activity service checks the calling package against the caller's uid, so shell answers for itself. */
    private fun Context.shellCallingPackage(): String? {
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(2000)
        return packageManager.getPackagesForUid(uid)?.firstOrNull() ?: SHELL_PACKAGE.takeIf { uid == 2000 }
    }

    /** Whether Shizuku's shell could be given [permission] through the package service, uselessly quiet otherwise. */
    private fun grantToShell(permission: String): Boolean =
        runCatching {
            val service = JavaBridge.iPackageManager(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
            val grant =
                Class.forName("android.content.pm.IPackageManager")
                    .methods
                    .firstOrNull { it.name == "grantRuntimePermission" && it.parameterCount == 3 }
                    ?: return false
            grant.invoke(service, SHELL_PACKAGE, permission, 0)
        }.isSuccess

    /**
     * The assist funnel: points Samsung's assistant at [pkg]/[activity] through the Shizuku shell, stirs the Assist
     * key, and puts the old target back once the start is under way.
     */
    private fun assistStart(context: Context, pkg: String, activity: String) {
        val before = runCatching { Settings.Secure.getString(context.contentResolver, ASSISTANT_SETTING) }.getOrNull()
        try {
            if (!context.runShizukuCommand("settings put secure $ASSISTANT_SETTING $pkg/$activity")) {
                throw IllegalStateException("the assistant setting wouldn't take the band menu")
            }
            try {
                launchAssist(context)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!context.runShizukuCommand("input keyevent $ASSIST_KEYCODE")) throw e
            }
            // The assist takes a moment to walk from the framework's trampoline to the activity it was pointed at.
            Thread.sleep(500)
        } finally {
            val putBack =
                if (before === null) "settings delete secure $ASSISTANT_SETTING"
                else "settings put secure $ASSISTANT_SETTING $before"
            runCatching { context.runShizukuCommand(putBack) }
        }
    }

    private fun launchAssist(context: Context) {
        val searchManager =
            context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
                ?: throw IllegalStateException("the search service is not on")
        SearchManager::class.java.getMethod("launchAssist", Bundle::class.java).invoke(searchManager, Bundle())
    }

    /** Runs a shell line under Shizuku and reports whether it came back clean. */
    private fun Context.runShizukuCommand(command: String): Boolean =
        try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return false
            val process = service.newProcess(arrayOf("/system/bin/sh", "-c", command), null, null)
            try {
                // Drain both ends, so a talkative command can't stall the pipe, and only a clean exit counts.
                ParcelFileDescriptor.AutoCloseInputStream(process.inputStream).copyTo(ByteArrayOutputStream())
                val err = ByteArrayOutputStream()
                ParcelFileDescriptor.AutoCloseInputStream(process.errorStream).copyTo(err)
                process.waitFor() == 0 && err.size() == 0
            } finally {
                runCatching { process.destroy() }
            }
        } catch (_: Exception) {
            false
        }
}
