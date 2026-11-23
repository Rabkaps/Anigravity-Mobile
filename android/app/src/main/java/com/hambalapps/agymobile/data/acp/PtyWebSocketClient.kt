package com.hambalapps.agymobile.data.acp

import com.hambalapps.agymobile.data.engine.StandaloneEngineManager
import com.hambalapps.agymobile.data.local.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class PtyWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _detectedUrl = MutableStateFlow<String?>(null)
    val detectedUrl: StateFlow<String?> = _detectedUrl.asStateFlow()

    private var socket: WebSocket? = null
    private var localProc: Process? = null
    private var localWriter: BufferedWriter? = null

    fun connect(profile: ConnectionProfile) {
        disconnect()
        val req = Request.Builder()
            .url(profile.ptyWebSocketUrl())
            .apply {
                if (profile.token.isNotBlank()) {
                    header("Authorization", "Bearer ${profile.token.trim()}")
                }
            }
            .build()

        socket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                appendOutput("[connected to ${profile.name} shell]\n")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                appendOutput(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                appendOutput("\n[disconnected: $reason]\n")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                appendOutput("\n[terminal error: ${t.message}]\n")
            }
        })
    }

    fun connectLocal(engineManager: StandaloneEngineManager, cwd: String) {
        disconnect()
        scope.launch {
            runCatching {
                engineManager.ensureBaseDirs()
                val workDir = cwd.takeIf { it.isNotBlank() && File(it).exists() }?.let(::File)
                    ?: engineManager.homeDir
                val shellCmd = if (File("/system/bin/sh").exists()) {
                    listOf("/system/bin/sh")
                } else {
                    engineManager.buildLinkerCommand(engineManager.bashBin)
                }
                val pb = ProcessBuilder(shellCmd)
                    .directory(workDir)
                    .redirectErrorStream(true)
                pb.environment().putAll(engineManager.buildChildEnvironment())
                val proc = pb.start()
                localProc = proc
                localWriter = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
                _connected.value = true
                appendOutput("[connected to standalone shell (${engineManager.detectArch().abi})]\n")

                val buf = ByteArray(4096)
                val inp = proc.inputStream
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    appendOutput(String(buf, 0, n, Charsets.UTF_8))
                }
                val code = proc.waitFor()
                if (localProc === proc) {
                    localProc = null
                    localWriter = null
                    _connected.value = false
                    appendOutput("\n[shell exited with code $code]\n")
                }
            }.onFailure { err ->
                _connected.value = false
                appendOutput("\n[standalone shell error: ${err.message}]\n")
            }
        }
    }

    fun sendCommand(line: String) {
        socket?.send(line + "\n")
        val w = localWriter
        if (w != null) {
            scope.launch {
                runCatching {
                    w.write(line + "\n")
                    w.flush()
                }
            }
        }
    }

    fun sendRaw(text: String) {
        socket?.send(text)
        val w = localWriter
        if (w != null) {
            scope.launch {
                runCatching {
                    if (text == "\u0003") {
                        // Interrupt local shell by restarting cleanly if Ctrl+C is tapped.
                        w.write("\u0003")
                    } else {
                        w.write(text)
                    }
                    w.flush()
                }
            }
        }
    }

    fun clearOutput() {
        _output.value = ""
        _detectedUrl.value = null
    }

    fun disconnect() {
        socket?.close(1000, "Terminal closed")
        socket = null
        runCatching { localWriter?.close() }
        localWriter = null
        runCatching { localProc?.destroy() }
        localProc = null
        _connected.value = false
        _detectedUrl.value = null
    }

    private fun appendOutput(chunk: String) {
        val combined = _output.value + chunk
        val trimmed = if (combined.length > 24_000) {
            combined.takeLast(24_000)
        } else {
            combined
        }
        _output.value = trimmed
        extractLatestUrl(trimmed)?.let { url ->
            if (_detectedUrl.value != url) {
                _detectedUrl.value = url
            }
        }
    }

    private fun extractLatestUrl(text: String): String? {
        val tail = if (text.length > 4_000) text.takeLast(4_000) else text
        val clean = ANSI_REGEX.replace(tail, "")
        val matches = URL_REGEX.findAll(clean)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .toList()
        if (matches.isEmpty()) return null
        return matches.lastOrNull { isAuthLikeUrl(it) } ?: matches.lastOrNull()
    }

    companion object {
        private val ANSI_REGEX = Regex("\\u001B\\[[0-9;?]*[A-Za-z]")
        private val URL_REGEX = Regex("https?://[^\\s)\\]\\}>\"']+")

        fun isAuthLikeUrl(url: String): Boolean {
            val lower = url.lowercase()
            return lower.contains("accounts.google.com") ||
                lower.contains("oauth") ||
                lower.contains("login") ||
                lower.contains("auth") ||
                lower.contains("127.0.0.1") ||
                lower.contains("localhost")
        }
    }
}
