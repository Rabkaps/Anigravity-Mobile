package com.hambalapps.agymobile.data.engine

import com.hambalapps.agymobile.data.acp.PtyWebSocketClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.URLDecoder

enum class GoogleLoginPhase {
    IDLE,
    STARTING,
    AWAITING_CODE,
    SUBMITTING_CODE,
    SUCCESS,
    ERROR
}

data class GoogleLoginState(
    val phase: GoogleLoginPhase = GoogleLoginPhase.IDLE,
    val authUrl: String? = null,
    val logOutput: String = "",
    val errorMessage: String? = null
)

/**
 * Drives `agy acp --login` in Remote-SSH OAuth mode (`SSH_CONNECTION` set),
 * so Google Sign-In never hangs waiting for a localhost callback while the browser is in the foreground.
 */
class StandaloneLoginRunner(
    private val engineManager: StandaloneEngineManager
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }
    )

    private val _state = MutableStateFlow(GoogleLoginState())
    val state: StateFlow<GoogleLoginState> = _state.asStateFlow()

    @Volatile
    private var proc: Process? = null
    @Volatile
    private var stdinWriter: BufferedWriter? = null
    private var loginJob: Job? = null
    private var tokenWatchJob: Job? = null
    @Volatile
    private var autoOpenedUrl: String? = null

    fun startLogin(onAuthenticated: () -> Unit) {
        cancelInternal(resetState = false)
        autoOpenedUrl = null
        val startTimeMs = System.currentTimeMillis()
        _state.value = GoogleLoginState(
            phase = GoogleLoginPhase.STARTING,
            logOutput = "Starting agy Google sign-in…\n"
        )

        tokenWatchJob = scope.launch {
            while (isActive) {
                delay(500L)
                val tok = engineManager.oauthTokenFile
                if (tok.exists() && tok.lastModified() >= startTimeMs - 1_000L && tok.length() > 20L) {
                    markSuccess(onAuthenticated)
                    break
                }
            }
        }

        loginJob = scope.launch {
            runCatching {
                engineManager.ensureBaseDirs()
                val cmd = engineManager.buildAgyCommand(listOf("acp", "--login"))
                val pb = ProcessBuilder(cmd)
                    .directory(engineManager.homeDir)
                    .redirectErrorStream(true)
                val env = engineManager.buildChildEnvironment()
                pb.environment().putAll(env)
                // Force deterministic Remote-SSH OAuth URL + Copy-Code flow so mobile browser backgrounding never times out.
                pb.environment()["SSH_CONNECTION"] = "127.0.0.1 22 127.0.0.1 22"
                pb.environment()["SSH_CLIENT"] = "127.0.0.1 22 22"

                val started = pb.start()
                proc = started
                stdinWriter = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))

                val buf = ByteArray(2048)
                val inp = started.inputStream
                while (isActive) {
                    val n = runCatching { inp.read(buf) }.getOrDefault(-1)
                    if (n <= 0) break
                    val chunk = ANSI_REGEX.replace(String(buf, 0, n, Charsets.UTF_8), "")
                    appendLogAndDetectUrl(chunk)
                }

                val exitCode = runCatching { started.waitFor() }.getOrDefault(-1)
                if (_state.value.phase == GoogleLoginPhase.SUCCESS) return@launch

                val tok = engineManager.oauthTokenFile
                if (tok.exists() && tok.lastModified() >= startTimeMs - 1_000L && tok.length() > 20L) {
                    markSuccess(onAuthenticated)
                } else if (_state.value.phase != GoogleLoginPhase.IDLE) {
                    _state.update {
                        it.copy(
                            phase = GoogleLoginPhase.ERROR,
                            errorMessage = "Sign-in process exited ($exitCode). Please try again or paste a fresh code."
                        )
                    }
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        phase = GoogleLoginPhase.ERROR,
                        errorMessage = err.message ?: "Failed to start agy login"
                    )
                }
            }
        }
    }

    fun submitCode(rawCode: String, onAuthenticated: () -> Unit) {
        val cleanCode = extractAuthCode(rawCode)
        if (cleanCode.isBlank()) {
            _state.update {
                it.copy(errorMessage = "Please paste the authorization code from your browser.")
            }
            return
        }

        val writer = stdinWriter
        if (writer == null) {
            _state.update {
                it.copy(
                    phase = GoogleLoginPhase.ERROR,
                    errorMessage = "Sign-in session is no longer active. Tap Start Google Sign-In again."
                )
            }
            return
        }

        _state.update {
            it.copy(
                phase = GoogleLoginPhase.SUBMITTING_CODE,
                errorMessage = null,
                logOutput = (it.logOutput + "\n[submitting authorization code…]\n").takeLast(8_000)
            )
        }

        scope.launch {
            runCatching {
                writer.write(cleanCode)
                writer.newLine()
                writer.flush()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        phase = GoogleLoginPhase.ERROR,
                        errorMessage = "Could not send code to agy: ${err.message}"
                    )
                }
                return@launch
            }

            // Wait up to 25s for token file or process completion
            repeat(50) {
                if (_state.value.phase == GoogleLoginPhase.SUCCESS) return@launch
                if (_state.value.phase == GoogleLoginPhase.ERROR) return@launch
                val tok = engineManager.oauthTokenFile
                if (tok.exists() && tok.length() > 20L &&
                    _state.value.phase == GoogleLoginPhase.SUBMITTING_CODE &&
                    proc?.isAlive == false
                ) {
                    markSuccess(onAuthenticated)
                    return@launch
                }
                delay(500L)
            }

            if (_state.value.phase == GoogleLoginPhase.SUBMITTING_CODE) {
                _state.update {
                    it.copy(
                        phase = GoogleLoginPhase.ERROR,
                        errorMessage = "Timed out verifying code. Check the CLI output or retry sign-in."
                    )
                }
            }
        }
    }

    fun cancel() {
        cancelInternal(resetState = true)
    }

    private fun cancelInternal(resetState: Boolean) {
        tokenWatchJob?.cancel()
        tokenWatchJob = null
        loginJob?.cancel()
        loginJob = null
        runCatching { stdinWriter?.close() }
        stdinWriter = null
        runCatching { proc?.destroy() }
        proc = null
        if (resetState) {
            _state.value = GoogleLoginState()
        }
    }

    private fun markSuccess(onAuthenticated: () -> Unit) {
        if (_state.value.phase == GoogleLoginPhase.SUCCESS) return
        _state.update {
            it.copy(
                phase = GoogleLoginPhase.SUCCESS,
                errorMessage = null,
                logOutput = (it.logOutput + "\n[Google sign-in complete!]\n").takeLast(8_000)
            )
        }
        tokenWatchJob?.cancel()
        runCatching { stdinWriter?.close() }
        stdinWriter = null
        runCatching { proc?.destroy() }
        proc = null
        onAuthenticated()
    }

    private fun appendLogAndDetectUrl(chunk: String) {
        if (chunk.isEmpty()) return
        val combined = (_state.value.logOutput + chunk).takeLast(8_000)
        val detected = URL_REGEX.findAll(combined)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .lastOrNull { PtyWebSocketClient.isAuthLikeUrl(it) }

        val shouldMoveToAwaiting = detected != null &&
            (_state.value.phase == GoogleLoginPhase.STARTING || _state.value.phase == GoogleLoginPhase.AWAITING_CODE)

        _state.update {
            it.copy(
                phase = if (shouldMoveToAwaiting) GoogleLoginPhase.AWAITING_CODE else it.phase,
                authUrl = detected ?: it.authUrl,
                logOutput = combined
            )
        }

        if (detected != null && detected != autoOpenedUrl) {
            autoOpenedUrl = detected
            engineManager.openUrlInBrowser(detected, force = false)
        }
    }

    companion object {
        private val ANSI_REGEX = Regex("\\u001B\\[[0-9;?]*[A-Za-z]")
        private val URL_REGEX = Regex("https?://[^\\s)\\]\\}>\"']+")

        fun extractAuthCode(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.contains("code=")) {
                val afterCode = trimmed.substringAfter("code=").substringBefore('&').substringBefore('#')
                val decoded = runCatching { URLDecoder.decode(afterCode, "UTF-8") }.getOrDefault(afterCode)
                if (decoded.isNotBlank()) return decoded.trim()
            }
            return trimmed
        }
    }
}
