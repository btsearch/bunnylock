package dev.ririxi.bunnylock.shizuku.samsung

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import dev.ririxi.bunnylock.shizuku.HiddenApi
import dev.ririxi.bunnylock.shizuku.ShizukuShell
import dev.ririxi.bunnylock.shizuku.invokeUnwrapped
import dev.ririxi.bunnylock.shizuku.shellQuoted
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

internal object AssistLaunch {
    private const val ASSISTANT_SETTING = "assistant"
    private const val UNSET = "null"
    private val START_SETTLE_TIME = 500.milliseconds

    suspend fun open(
        context: Context,
        menu: ComponentName,
        extras: Bundle,
    ) {
        // Read through the shell like the writes, so an unreadable setting can't pass for an unset one.
        val previous = ShizukuShell.run("settings get secure $ASSISTANT_SETTING")
        ShizukuShell.run("settings put secure $ASSISTANT_SETTING ${menu.flattenToShortString()}")
        try {
            launch(context, extras)
            delay(START_SETTLE_TIME)
        } finally {
            withContext(NonCancellable) { restore(previous) }
        }
    }

    private suspend fun launch(
        context: Context,
        extras: Bundle,
    ) {
        val searchManager = checkNotNull(context.getSystemService(SearchManager::class.java)) { "The search service is missing" }
        try {
            HiddenApi.method("android.app.SearchManager", "launchAssist", 1).invokeUnwrapped(searchManager, extras)
        } catch (e: Exception) {
            try {
                ShizukuShell.run("input keyevent ${KeyEvent.KEYCODE_ASSIST}")
            } catch (_: IOException) {
                throw e
            }
        }
    }

    private suspend fun restore(previous: String) {
        val command =
            if (previous == UNSET) {
                "settings delete secure $ASSISTANT_SETTING"
            } else {
                "settings put secure $ASSISTANT_SETTING ${previous.shellQuoted()}"
            }
        ShizukuShell.run(command)
    }
}
