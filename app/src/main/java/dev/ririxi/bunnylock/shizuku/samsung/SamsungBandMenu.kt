package dev.ririxi.bunnylock.shizuku.samsung

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.ririxi.bunnylock.backend.samsung.Samsung
import dev.ririxi.bunnylock.data.RootException
import dev.ririxi.bunnylock.modem.R as ModemR
import dev.ririxi.bunnylock.modem.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SamsungBandMenu {
    private val SERVICE_MODE = ComponentName.createRelative(Samsung.SERVICE_MODE_PACKAGE, Samsung.SERVICE_MODE_ACTIVITY)

    private const val TELECOM_ACTION = "com.samsung.server.telecom.USER_SELECT_WIFI_SERVICE_CALL"
    private const val TELECOM_INTENT_EXTRA = "extra_call_intent"

    private class Route(
        val name: String,
        val open: suspend () -> Unit,
    )

    suspend fun open(context: Context) {
        withContext(Dispatchers.IO) {
            val extras = Bundle().apply { putString(Samsung.DIALER_CODE_EXTRA, Samsung.BAND_SELECTION_DIALER_CODE) }
            val intent = Intent().setComponent(SERVICE_MODE).putExtras(extras)
            val routes =
                listOf(
                    Route("Shizuku") { ActivityStart.open(intent) },
                    // A broadcast can't tell whether anyone acted on it, so this route counts as done once it is sent.
                    Route("Telecom") { context.sendBroadcast(Intent(TELECOM_ACTION).putExtra(TELECOM_INTENT_EXTRA, intent)) },
                    Route("Assist") { AssistLaunch.open(context, SERVICE_MODE, extras) },
                )
            val failures = ArrayList<String>()
            for (route in routes) {
                try {
                    route.open()
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${route.name}: ${e.message.orEmpty().ifBlank { e.javaClass.simpleName }}"
                }
            }
            throw RootException(context, failureText(failures))
        }
    }

    private fun failureText(failures: List<String>): Text {
        val denied = failures.any { Samsung.PERMISSION_DENIAL in it }
        val reason = if (denied) ModemR.string.sys_samsung_menu_denied else ModemR.string.sys_samsung_menu_missing
        return Text(ModemR.string.aosp_with_details, Text(reason), failures.joinToString(" / "))
    }
}
