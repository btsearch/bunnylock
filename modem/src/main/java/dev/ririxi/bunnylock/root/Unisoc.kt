package dev.ririxi.bunnylock.root

import android.os.IBinder
import android.os.Parcel
import dev.ririxi.bunnylock.modem.R
import dev.ririxi.bunnylock.modem.UnisocLteMask
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * Unisoc's `IRadioInteractor` AT passthrough. This is the binder behind the working shell command:
 *
 *   service call irit 2 s16 "AT+SPLBAND=0" i32 1 s16 "x" i32 1
 *
 * The helper runs as root (or as the Shizuku shell user), so it can call the same binder directly.
 * The transaction signature is `sendAtCmd(String, String[], int)`; transaction code 2 is
 * `TRANSACTION_sendAtCmd` in IRadioInteractor.aidl.
 */
object Unisoc {
    private const val SERVICE = "irit"
    private const val DESCRIPTOR = "com.android.unisoc.telephony.aidl.IRadioInteractor"
    private const val TRANSACTION_SEND_AT = 2
    private const val REPLY_SLOT = "x"

    private val getService: Method? by lazy {
        runCatching {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
        }.getOrNull()
    }

    private fun phoneId(sim: Int): Int = (sim - 1).coerceAtLeast(0)

    private fun binder(): IBinder? = getService?.invoke(null, SERVICE) as? IBinder

    /** Reads the modem's LTE mask through `AT+SPLBAND=0`. */
    fun state(sim: Int): JSONObject {
        val phoneId = phoneId(sim)
        val raw = sendAt("AT+SPLBAND=0", phoneId)
        val mask = UnisocLteMask.parse(raw) ?: fail(R.string.unisoc_at_bad_reply, "AT+SPLBAND=0", raw.trim())
        return JSONObject()
            .put("phoneId", phoneId)
            .put("mask", mask.readback)
            .put("bands", JSONArray(mask.bands()))
            .put("raw", raw)
    }

    /** Writes the mask and reboots the modem so the new band set takes effect. */
    fun setBands(sim: Int, mask: String): JSONObject {
        val phoneId = phoneId(sim)
        val setCommand = "AT+SPLBAND=1,$mask"
        val setReply = sendAt(setCommand, phoneId)
        if (!accepted(setReply)) fail(R.string.unisoc_at_not_accepted, setCommand, setReply.trim())
        val resetReply = sendAt("AT+RESET=0", phoneId)
        if (!accepted(resetReply)) fail(R.string.unisoc_at_not_accepted, "AT+RESET=0", resetReply.trim())
        return JSONObject()
            .put("phoneId", phoneId)
            .put("mask", mask)
            .put("set", setReply)
            .put("reset", resetReply)
    }

    private fun accepted(reply: String): Boolean = reply.lineSequence().any { it.trim() == "OK" }

    private fun sendAt(command: String, phoneId: Int): String {
        val binder = binder() ?: fail(R.string.unisoc_no_service)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(command)
            data.writeStringArray(arrayOf(REPLY_SLOT))
            data.writeInt(phoneId)
            if (!binder.transact(TRANSACTION_SEND_AT, data, reply, 0)) fail(R.string.unisoc_at_refused, command)
            try {
                reply.readException()
            } catch (e: Exception) {
                fail(R.string.unisoc_at_refused, command, e.message ?: e.javaClass.simpleName)
            }
            val result = reply.readInt()
            val lines =
                reply.createStringArray()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            if (lines.isEmpty()) fail(R.string.unisoc_at_no_reply, command, result)
            return lines.joinToString("\n")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
