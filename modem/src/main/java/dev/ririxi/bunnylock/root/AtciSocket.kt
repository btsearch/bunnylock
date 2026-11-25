package dev.ririxi.bunnylock.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import android.system.Os
import dev.ririxi.bunnylock.modem.R
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/** The vendor rild-atci socket takes a big-endian length followed by a CR-terminated AT command. */
internal object AtciSocket {
    const val NODE = "/dev/socket/rild-atci"
    private const val SLOT_PROPERTY = "persist.vendor.service.atci.sim"
    private const val MAX_REPLY_CHARS = 65536

    fun available(): Boolean = Os.getuid() == 0 && servesFirstSlot() && File(NODE).exists()

    // The vendor daemon reads an unset property as slot 0.
    private fun servesFirstSlot() = Sys.prop(SLOT_PROPERTY).let { it.isEmpty() || it == "0" }

    fun send(
        command: String,
        timeoutMs: Int,
    ): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        LocalSocket().use { socket ->
            try {
                socket.connect(LocalSocketAddress(NODE, LocalSocketAddress.Namespace.FILESYSTEM))
            } catch (e: IOException) {
                fail(R.string.at_open_failed, NODE, e.toText())
            }
            val bytes = (command + "\r").toByteArray()
            try {
                DataOutputStream(socket.outputStream).run {
                    writeInt(bytes.size)
                    write(bytes)
                    flush()
                }
            } catch (e: IOException) {
                fail(R.string.at_read_failed, NODE, e.toText())
            }
            return readReply(socket, deadline)
        }
    }

    private fun readReply(
        socket: LocalSocket,
        deadline: Long,
    ): String {
        val reply = StringBuilder()
        val buffer = ByteArray(4096)
        while (reply.length < MAX_REPLY_CHARS && !endsWithFinalResult(reply)) {
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) break
            socket.soTimeout = remainingMs.toInt()
            val count =
                try {
                    socket.inputStream.read(buffer)
                } catch (e: IOException) {
                    if (SystemClock.elapsedRealtime() >= deadline) break
                    fail(R.string.at_read_failed, NODE, e.toText())
                }
            if (count <= 0) break
            reply.append(String(buffer, 0, count, Charsets.ISO_8859_1))
        }
        return reply.toString()
    }

    private fun endsWithFinalResult(reply: CharSequence): Boolean {
        val lastLineEnd = reply.lastIndexOfAny(charArrayOf('\r', '\n'))
        return lastLineEnd >= 0 && At.hasFinalResult(reply.subSequence(0, lastLineEnd))
    }
}
