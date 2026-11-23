package com.hambalapps.agymobile.data.engine

import android.os.Bundle
import java.io.ByteArrayOutputStream
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
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { process.inputStream.copyTo(stdout) }.apply { start() }
            val errThread = Thread { process.errorStream.copyTo(stderr) }.apply { start() }
            val exitCode = process.waitFor()
            outThread.join(5000)
            errThread.join(5000)
            result.putInt("exitCode", exitCode)
            result.putString("stdout", stdout.toString("UTF-8"))
            result.putString("stderr", stderr.toString("UTF-8"))
        } catch (t: Throwable) {
            result.putInt("exitCode", 127)
            result.putString("stdout", "")
            result.putString("stderr", t.message ?: t.javaClass.simpleName)
        }
        return result
    }
}
