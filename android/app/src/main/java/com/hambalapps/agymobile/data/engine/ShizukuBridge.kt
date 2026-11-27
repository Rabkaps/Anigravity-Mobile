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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
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
    private var appContext: Context? = null

    @Volatile
    private var service: IShizukuShellService? = null

    @Volatile
    private var pendingBind: CompletableDeferred<IShizukuShellService?>? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var localPort: Int = 0
        private set

    val localToken: String by lazy {
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "ShizukuBridge").apply { isDaemon = true }
    }

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
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    fun startLocalRishServer(): Int {
        if (localPort > 0 && serverSocket?.isClosed == false) return localPort
        synchronized(this) {
            if (localPort > 0 && serverSocket?.isClosed == false) return localPort
            return runCatching {
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
                localPort
            }.getOrDefault(0)
        }
    }

    private suspend fun ensureService(context: Context): IShizukuShellService? {
        appContext = context.applicationContext
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
                        synchronized(this@ShizukuBridge) {
                            service = svc
                            if (pendingBind === def) pendingBind = null
                        }
                        def.complete(svc)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        synchronized(this@ShizukuBridge) {
                            service = null
                            if (pendingBind === def) pendingBind = null
                        }
                        if (!def.isCompleted) def.complete(null)
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    runCatching { Shizuku.bindUserService(args, conn) }.onFailure {
                        synchronized(this@ShizukuBridge) {
                            if (pendingBind === def) pendingBind = null
                        }
                        if (!def.isCompleted) def.complete(null)
                    }
                }
            }
        }

        val bound = withTimeoutOrNull(5000L) { deferred.await() }
        if (bound == null) {
            synchronized(this) {
                if (pendingBind === deferred) pendingBind = null
            }
            if (!deferred.isCompleted) deferred.complete(null)
        }
        return bound
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
            sock.soTimeout = 15_000
            val inp = BufferedInputStream(sock.getInputStream())
            val headerBytes = readHttpHeaderBytes(inp) ?: return
            val headerText = String(headerBytes, Charsets.US_ASCII)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val method = requestLine.substringBefore(" ").uppercase()
            if (method != "POST") {
                sendHttpResponse(sock, "HTTP/1.1 405 Method Not Allowed", 126, "POST required\n".toByteArray(Charsets.UTF_8))
                return
            }

            var contentLength = 0
            var providedToken = ""
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                when {
                    key.equals("Content-Length", ignoreCase = true) -> {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                    key.equals("X-Agy-Token", ignoreCase = true) -> {
                        providedToken = value
                    }
                }
            }

            if (!MessageDigest.isEqual(
                    providedToken.toByteArray(Charsets.UTF_8),
                    localToken.toByteArray(Charsets.UTF_8)
                )
            ) {
                sendHttpResponse(sock, "HTTP/1.1 403 Forbidden", 126, "Forbidden\n".toByteArray(Charsets.UTF_8))
                return
            }

            val bodyBytes = readExactBytes(inp, contentLength.coerceIn(0, 1_000_000))
            val cmd = String(bodyBytes, Charsets.UTF_8)

            var svc = service
            if (svc == null || !runCatching { svc.asBinder().pingBinder() }.getOrDefault(false)) {
                val ctx = appContext
                if (ctx != null && hasPermission()) {
                    svc = runCatching { runBlocking { ensureService(ctx) } }.getOrNull()
                }
            }

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
            sendHttpResponse(sock, statusLine, exitCode, bytes)
        }
    }

    private fun sendHttpResponse(sock: Socket, statusLine: String, exitCode: Int, body: ByteArray) {
        val header = "$statusLine\r\nX-Exit-Code: $exitCode\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        sock.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
        sock.getOutputStream().write(body)
        sock.getOutputStream().flush()
    }

    private fun readHttpHeaderBytes(inp: BufferedInputStream): ByteArray? {
        val out = ByteArrayOutputStream(512)
        var state = 0
        while (out.size() < 32_768) {
            val b = inp.read()
            if (b == -1) return null
            out.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) return out.toByteArray() else if (b == '\r'.code) 1 else 0
                else -> 0
            }
        }
        return null
    }

    private fun readExactBytes(inp: BufferedInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = inp.read(buf, off, length - off)
            if (n <= 0) break
            off += n
        }
        return if (off == length) buf else buf.copyOf(off)
    }

    suspend fun warmUp(context: Context): Boolean = withContext(Dispatchers.IO) {
        appContext = context.applicationContext
        startLocalRishServer()
        ensureService(context) != null
    }
}
