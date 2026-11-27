package com.hambalapps.agymobile.data.engine

import com.hambalapps.agymobile.data.acp.AcpClientEvent
import com.hambalapps.agymobile.data.acp.AcpParser
import com.hambalapps.agymobile.data.acp.AcpRpcException
import com.hambalapps.agymobile.data.acp.JsonRpcEnvelope
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Direct stdio JSON-RPC 2.0 host for `agy acp` inside Agy Mobile's own sandbox,
 * replacing the Termux + Node.js WebSocket bridge when using Standalone mode.
 */
class LocalAcpClient(
    private val engineManager: StandaloneEngineManager
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private val writeLock = Any()

    private val _connectionState = MutableStateFlow(BridgeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BridgeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AcpClientEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<AcpClientEvent> = _events.asSharedFlow()

    @Volatile
    private var proc: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private val sessionGeneration = AtomicLong(0L)
    @Volatile
    private var processJob: Job? = null
    private var activeCwd: String = engineManager.homeDir.absolutePath
    private var activeExtraDirs: List<String> = emptyList()
    private val envOverrides = ConcurrentHashMap<String, String>()

    fun setEnvOverrides(patch: Map<String, String>) {
        patch.forEach { (k, v) ->
            if (v.isBlank()) envOverrides.remove(k) else envOverrides[k] = v
        }
    }

    fun start(cwd: String? = null, extraDirs: List<String> = activeExtraDirs) {
        disconnect()
        if (!engineManager.isAgyInstalled()) {
            _connectionState.value = BridgeConnectionState.DISCONNECTED
            return
        }

        val gen = sessionGeneration.incrementAndGet()
        val targetDir = cwd?.takeIf { it.isNotBlank() && File(it).exists() }?.let(::File)
            ?: engineManager.homeDir.also { it.mkdirs() }
        activeCwd = targetDir.absolutePath
        activeExtraDirs = extraDirs.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        _connectionState.value = BridgeConnectionState.CONNECTING

        processJob = scope.launch {
            runCatching {
                val acpArgs = buildList {
                    add("acp")
                    activeExtraDirs.forEach { dir ->
                        if (File(dir).exists()) {
                            add("--add-dir")
                            add(dir)
                        }
                    }
                }
                val cmd = engineManager.buildAgyCommand(acpArgs)
                val pb = ProcessBuilder(cmd)
                    .directory(targetDir)
                    .redirectErrorStream(false)
                pb.environment().putAll(engineManager.buildChildEnvironment())
                pb.environment().putAll(envOverrides)

                if (sessionGeneration.get() != gen) return@launch
                val started = pb.start()
                if (sessionGeneration.get() != gen) {
                    runCatching { started.destroyForcibly() }
                    return@launch
                }

                proc = started
                synchronized(writeLock) {
                    stdinWriter = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
                }
                _connectionState.value = BridgeConnectionState.CONNECTED

                launch {
                    runCatching {
                        started.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (sessionGeneration.get() == gen &&
                                    (line.contains("FATAL", ignoreCase = true) ||
                                        line.contains("panic:", ignoreCase = true))
                                ) {
                                    _events.emit(AcpClientEvent.BridgeNotice(line))
                                }
                            }
                        }
                    }
                }

                started.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val rawLine = reader.readLine() ?: break
                        if (sessionGeneration.get() != gen) break
                        val line = rawLine.trim()
                        if (line.isNotEmpty()) {
                            runCatching { handleIncoming(line) }
                        }
                    }
                }

                val exitCode = started.waitFor()
                if (proc === started && sessionGeneration.get() == gen) {
                    proc = null
                    synchronized(writeLock) { stdinWriter = null }
                    failAllPending("agy acp exited ($exitCode)")
                    _connectionState.value = BridgeConnectionState.DISCONNECTED
                    _events.emit(AcpClientEvent.BridgeNotice("Standalone agy acp exited ($exitCode)"))
                }
            }.onFailure { err ->
                if (sessionGeneration.get() == gen) {
                    failAllPending(err.message ?: "Failed to start agy acp")
                    _connectionState.value = BridgeConnectionState.ERROR
                    _events.emit(
                        AcpClientEvent.BridgeNotice("Standalone agy launch failed: ${err.message}")
                    )
                }
            }
        }
    }

    fun disconnect() {
        sessionGeneration.incrementAndGet()
        processJob?.cancel()
        processJob = null
        val current = proc
        proc = null
        synchronized(writeLock) {
            runCatching { stdinWriter?.close() }
            stdinWriter = null
        }
        if (current != null) {
            runCatching {
                current.destroy()
                if (current.isAlive) {
                    scope.launch {
                        kotlinx.coroutines.delay(500L)
                        if (current.isAlive) runCatching { current.destroyForcibly() }
                    }
                }
            }
        }
        failAllPending("Disconnected")
        _connectionState.value = BridgeConnectionState.DISCONNECTED
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private suspend fun handleIncoming(raw: String) {
        val envelope = runCatching { json.decodeFromString<JsonRpcEnvelope>(raw) }.getOrNull() ?: return

        if (envelope.id != null && envelope.id !is JsonNull && envelope.method == null) {
            val key = envelope.id.jsonPrimitive.contentOrNull ?: envelope.id.toString()
            val waiter = pending.remove(key) ?: return
            if (envelope.error != null) {
                waiter.completeExceptionally(
                    AcpRpcException(envelope.error.code, envelope.error.message)
                )
            } else {
                waiter.complete(envelope.result ?: buildJsonObject { })
            }
            return
        }

        if (envelope.id != null && envelope.method == "session/request_permission" && envelope.params != null) {
            val parsed = AcpParser.parsePermissionRequest(envelope.id, envelope.params)
            _events.emit(AcpClientEvent.PermissionRequested(parsed))
            return
        }
        if (envelope.id != null && envelope.method == "elicitation/create" && envelope.params != null) {
            val parsed = AcpParser.parseElicitationRequest(envelope.id, envelope.params)
            _events.emit(AcpClientEvent.ElicitationRequested(parsed))
            return
        }

        if (envelope.method == "session/update" && envelope.params != null) {
            AcpParser.parseSessionUpdate(envelope.params)?.let { ev ->
                _events.emit(AcpClientEvent.SessionUpdate(ev))
            }
        }
    }

    suspend fun sendRequest(
        method: String,
        params: JsonObject,
        timeoutMs: Long = 120_000L
    ): JsonElement = withContext(Dispatchers.IO) {
        val finalParams = maybeInjectSystemPrompt(method, maybeInjectAndroidMcp(method, params))
        val idNum = nextId.getAndIncrement()
        val idKey = idNum.toString()
        val deferred = CompletableDeferred<JsonElement>()
        pending[idKey] = deferred

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", JsonPrimitive(idNum))
            put("method", method)
            put("params", finalParams)
        }.toString()

        runCatching {
            synchronized(writeLock) {
                val writer = stdinWriter ?: throw IllegalStateException("Standalone agy acp is not running")
                writer.write(payload)
                writer.newLine()
                writer.flush()
            }
        }.onFailure { err ->
            pending.remove(idKey)
            throw IllegalStateException("Failed to write to agy acp: ${err.message}")
        }

        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pending.remove(idKey)
        }
    }

    fun sendNotification(method: String, params: JsonObject) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }.toString()
        writeRawLineAsync(payload)
    }

    fun sendPermissionReply(rpcId: JsonElement, optionId: String?) {
        writeRawLineAsync(AcpParser.buildPermissionResponse(rpcId, optionId).toString())
    }

    fun sendElicitationReply(rpcId: JsonElement, action: String, content: Map<String, String> = emptyMap()) {
        writeRawLineAsync(AcpParser.buildElicitationResponse(rpcId, action, content).toString())
    }

    private fun writeRawLineAsync(line: String) {
        scope.launch {
            synchronized(writeLock) {
                val writer = stdinWriter ?: return@synchronized
                runCatching {
                    writer.write(line)
                    writer.newLine()
                    writer.flush()
                }
            }
        }
    }

    private fun maybeInjectAndroidMcp(method: String, params: JsonObject): JsonObject {
        if (method !in setOf("session/new", "session/load", "session/resume")) return params
        if (!engineManager.isNodeMcpReady()) return params

        val existing = runCatching { params["mcpServers"]?.jsonArray?.toList() }.getOrNull().orEmpty()
        val alreadyRegistered = existing.any {
            runCatching { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == "android-control" }.getOrDefault(false)
        }
        if (alreadyRegistered) return params

        val childEnv = engineManager.buildChildEnvironment()
        val mcpCmd = engineManager.buildLinkerCommand(
            engineManager.nodeBin,
            listOf(engineManager.mcpScript.absolutePath)
        )

        val serverObj = buildJsonObject {
            put("name", "android-control")
            put("command", mcpCmd.first())
            put("args", buildJsonArray {
                mcpCmd.drop(1).forEach { add(JsonPrimitive(it)) }
            })
            put("env", buildJsonArray {
                childEnv.forEach { (k, v) ->
                    add(buildJsonObject {
                        put("name", k)
                        put("value", v)
                    })
                }
            })
        }

        return buildJsonObject {
            params.forEach { (k, v) ->
                if (k != "mcpServers") put(k, v)
            }
            put("mcpServers", buildJsonArray {
                existing.forEach { add(it) }
                add(serverObj)
            })
        }
    }

    private suspend fun maybeInjectSystemPrompt(method: String, params: JsonObject): JsonObject {
        if (method !in setOf("session/new", "session/load", "session/resume")) return params
        val existingMeta = runCatching { params["_meta"]?.jsonObject }.getOrNull()
        val existingAgy = runCatching { existingMeta?.get("antigravity")?.jsonObject }.getOrNull()
        if (existingAgy?.containsKey("systemPrompt") == true) return params

        val privilegeLevel = engineManager.detectPrivilegeLevel()
        engineManager.ensureMobileEnvRules(privilegeLevel)
        val contextPrompt = engineManager.buildDeviceContextPrompt(privilegeLevel)

        val mergedAgy = buildJsonObject {
            existingAgy?.forEach { (k, v) -> put(k, v) }
            put("systemPrompt", buildJsonObject {
                put("append", contextPrompt)
            })
        }
        val mergedMeta = buildJsonObject {
            existingMeta?.forEach { (k, v) -> if (k != "antigravity") put(k, v) }
            put("antigravity", mergedAgy)
        }
        return buildJsonObject {
            params.forEach { (k, v) -> if (k != "_meta") put(k, v) }
            put("_meta", mergedMeta)
        }
    }

    private fun failAllPending(reason: String) {
        val keys = pending.keys.toList()
        for (k in keys) {
            pending.remove(k)?.completeExceptionally(IllegalStateException(reason))
        }
    }
}
