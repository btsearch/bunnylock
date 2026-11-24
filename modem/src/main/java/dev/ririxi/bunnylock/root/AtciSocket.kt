package dev.ririxi.bunnylock.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import android.system.Os
import dev.ririxi.bunnylock.modem.R
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/** MediaTek's RIL takes a big-endian length followed by a CR-terminated AT command. */
internal object AtciSocket {
    const val NODE = "/dev/socket/rild-atci"
    private const val SLOT_PROPERTY = "persist.vendor.service.atci.sim"
    private const val MAX_COMMAND_BYTES = 8192
    private const val MAX_REPLY_BYTES = 65536

    // Only slot 1 has been verified. The vendor daemon uses zero when this property is unset.
    fun available(): Boolean =
        Os.getuid() == 0 && Sys.prop(SLOT_PROPERTY).let { it.isEmpty() || it == "0" } && File(NODE).exists()

    fun send(command: String, timeoutMs: Int, isComplete: (String) -> Boolean): String {
        if (!available()) fail(R.string.atci_slot_unavailable)
        val bytes = (command + "\r").toByteArray(Charsets.UTF_8)
        require(command.none { it == '\r' || it == '\n' || it == '\u0000' })
        require(bytes.size <= MAX_COMMAND_BYTES)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        try {
            return LocalSocket().use { socket ->
                socket.connect(LocalSocketAddress(NODE, LocalSocketAddress.Namespace.FILESYSTEM))
                val output = DataOutputStream(socket.outputStream)
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()

                val reply = StringBuilder()
                val buffer = ByteArray(4096)
                while (reply.length < MAX_REPLY_BYTES) {
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining.toInt()
                    val count = socket.inputStream.read(buffer)
                    if (count <= 0) break
                    reply.append(String(buffer, 0, count, Charsets.ISO_8859_1))
                    val raw = reply.toString()
                    if (isComplete(raw)) return@use raw
                }
                fail(R.string.at_no_reply, command, NODE)
            }
        } catch (e: IOException) {
            fail(R.string.at_read_failed, NODE, e.toText())
        }
    }
}
