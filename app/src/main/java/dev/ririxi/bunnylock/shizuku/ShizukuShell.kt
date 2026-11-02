package dev.ririxi.bunnylock.shizuku

import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

internal object ShizukuShell {
    private val TIMEOUT = 10.seconds

    private data class Reply(
        val output: String,
        val exitCode: Int,
    )

    suspend fun run(command: String): String {
        val server = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: throw IOException("Shizuku is not running")
        val process =
            try {
                RemoteShell(server.newProcess(arrayOf("/system/bin/sh", "-c", "$command 2>&1"), null, null))
            } catch (e: RemoteException) {
                throw IOException(e.message, e)
            }
        // A blocked read can't be interrupted, so it is left on its own thread until destroy() closes the streams.
        val reply = CompletableDeferred<Reply>()
        thread(name = "bunny-shizuku-shell", isDaemon = true) {
            reply.completeWith(runCatching { Reply(process.inputStream.bufferedReader().readText(), process.waitFor()) })
        }
        try {
            val (output, exitCode) =
                withTimeoutOrNull(TIMEOUT) { reply.await() } ?: throw IOException("`$command` didn't finish within $TIMEOUT")
            if (exitCode != 0) throw IOException(output.ifBlank { "`$command` exited with $exitCode" }.trim())
            return output.trim()
        } finally {
            process.destroy()
        }
    }
}

internal fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
