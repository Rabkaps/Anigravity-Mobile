package com.hambalapps.agymobile.data.engine

import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running in uid=2000 (ADB) or uid=0 (root).
 */
class ShizukuShellService : IShizukuShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Bundle {
        val result = Bundle()
        try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command).start()
            runCatching { process.outputStream.close() }
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { readBounded(process.inputStream, stdout) }.apply {
                isDaemon = true
                start()
            }
            val errThread = Thread { readBounded(process.errorStream, stderr) }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            outThread.join(2_000)
            errThread.join(2_000)
            val exitCode = if (finished) process.exitValue() else 124
            val errText = stderr.toString("UTF-8") + if (!finished) "\n[command timed out after ${COMMAND_TIMEOUT_SEC}s]" else ""
            result.putInt("exitCode", exitCode)
            result.putString("stdout", stdout.toString("UTF-8"))
            result.putString("stderr", errText)
        } catch (t: Throwable) {
            result.putInt("exitCode", 127)
            result.putString("stdout", "")
            result.putString("stderr", t.message ?: t.javaClass.simpleName)
        }
        return result
    }

    private fun readBounded(stream: InputStream, dest: ByteArrayOutputStream) {
        val buf = ByteArray(8192)
        var total = 0
        var truncated = false
        runCatching {
            stream.use { inp ->
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    val remaining = MAX_STREAM_BYTES - total
                    if (remaining > 0) {
                        val toWrite = minOf(n, remaining)
                        dest.write(buf, 0, toWrite)
                        total += toWrite
                    } else if (!truncated) {
                        truncated = true
                    }
                }
            }
            if (truncated) {
                dest.write("\n[output truncated at 256 KB]".toByteArray(Charsets.UTF_8))
            }
        }
    }

    companion object {
        private const val COMMAND_TIMEOUT_SEC = 60L
        private const val MAX_STREAM_BYTES = 256 * 1024
    }
}
