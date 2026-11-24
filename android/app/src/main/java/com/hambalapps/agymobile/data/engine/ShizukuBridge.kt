package com.hambalapps.agymobile.data.engine

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.hambalapps.agymobile.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

data class ShizukuExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

/**
 * Manages Shizuku binder permission, UserService binding, and a local loopback HTTP shim
 * so Standalone `rish` commands can execute with Shizuku ADB privileges (`uid=2000`).
 */
object ShizukuBridge {
    private const val REQUEST_CODE = 4791

    @Volatile
    private var service: IShizukuShellService? = null

    @Volatile
    private var pendingBind: CompletableDeferred<IShizukuShellService?>? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var localPort: Int = 0
        private set

    private val executor = Executors.newCachedThreadPool()

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return runCatching {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermissionIfNeeded() {
        if (!isBinderAlive() || hasPermission()) return
        runCatching {
            if (!Shizuku.isPreV11() && !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(REQUEST_CODE)
            } else if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    fun startLocalRishServer(): Int {
        if (localPort > 0 && serverSocket?.isClosed == false) return localPort
        synchronized(this) {
            if (localPort > 0 && serverSocket?.isClosed == false) return localPort
            val ss = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            localPort = ss.localPort
            executor.execute {
                runCatching {
                    while (!ss.isClosed) {
                        val client = runCatching { ss.accept() }.getOrNull() ?: break
                        executor.execute { runCatching { handleLocalHttp(client) } }
                    }
                }
            }
            return localPort
        }
    }

    private suspend fun ensureService(context: Context): IShizukuShellService? {
        service?.let { if (it.asBinder().pingBinder()) return it }
        if (!hasPermission()) return null

        val deferred = synchronized(this) {
            pendingBind ?: CompletableDeferred<IShizukuShellService?>().also { def ->
                pendingBind = def
                val args = Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, ShizukuShellService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("shizuku_shell")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE)

                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val svc = binder?.let { IShizukuShellService.Stub.asInterface(it) }
                        service = svc
                        pendingBind = null
                        def.complete(svc)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                        pendingBind = null
                        if (!def.isCompleted) def.complete(null)
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    runCatching { Shizuku.bindUserService(args, conn) }.onFailure {
                        pendingBind = null
                        if (!def.isCompleted) def.complete(null)
                    }
                }
            }
        }

        return withTimeoutOrNull(5000L) { deferred.await() }
    }

    suspend fun exec(context: Context, command: String): ShizukuExecResult = withContext(Dispatchers.IO) {
        val svc = ensureService(context)
            ?: return@withContext ShizukuExecResult(
                exitCode = 126,
                stdout = "",
                stderr = "Shizuku service unavailable or permission not granted"
            )
        runCatching {
            val bundle = svc.exec(command)
            ShizukuExecResult(
                exitCode = bundle.getInt("exitCode", 1),
                stdout = bundle.getString("stdout").orEmpty(),
                stderr = bundle.getString("stderr").orEmpty()
            )
        }.getOrElse { err ->
            ShizukuExecResult(127, "", err.message ?: "Shizuku exec failed")
        }
    }

    private fun handleLocalHttp(socket: Socket) {
        socket.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val bodyChars = CharArray(contentLength.coerceIn(0, 1_000_000))
            var read = 0
            while (read < bodyChars.size) {
                val n = reader.read(bodyChars, read, bodyChars.size - read)
                if (n <= 0) break
                read += n
            }
            val body = String(bodyChars, 0, read)
            val cmd = if (requestLine.startsWith("POST ")) {
                body
            } else {
                val query = requestLine.substringAfter(" ", "").substringBefore(" ").substringAfter("cmd=", "")
                URLDecoder.decode(query, "UTF-8")
            }

            val svc = service
            val (exitCode, outText) = if (svc != null && runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)) {
                runCatching {
                    val b = svc.exec(cmd)
                    val code = b.getInt("exitCode", 1)
                    val out = b.getString("stdout").orEmpty()
                    val err = b.getString("stderr").orEmpty()
                    code to (out + err)
                }.getOrElse { err ->
                    126 to "Shizuku exec error: ${err.message}\n"
                }
            } else {
                126 to "Shizuku service not bound. Open Agy Mobile and grant Shizuku permission.\n"
            }

            val bytes = outText.toByteArray(Charsets.UTF_8)
            val statusLine = if (exitCode == 0) "HTTP/1.1 200 OK" else "HTTP/1.1 500 Command Failed"
            val header = "$statusLine\r\nX-Exit-Code: $exitCode\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            sock.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
            sock.getOutputStream().write(bytes)
            sock.getOutputStream().flush()
        }
    }

    suspend fun warmUp(context: Context): Boolean = withContext(Dispatchers.IO) {
        startLocalRishServer()
        ensureService(context) != null
    }
}
