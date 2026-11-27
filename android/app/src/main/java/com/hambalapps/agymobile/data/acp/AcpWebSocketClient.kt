package com.hambalapps.agymobile.data.acp

import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface AcpClientEvent {
    data class SessionUpdate(val event: AcpSessionEvent) : AcpClientEvent
    data class PermissionRequested(val request: AcpPermissionRequest) : AcpClientEvent
    data class ElicitationRequested(val request: AcpElicitationRequest) : AcpClientEvent
    data class BridgeNotice(val message: String) : AcpClientEvent
}

class AcpWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    private val _connectionState = MutableStateFlow(BridgeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BridgeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AcpClientEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<AcpClientEvent> = _events.asSharedFlow()

    @Volatile
    private var activeSocket: WebSocket? = null
    @Volatile
    private var currentProfile: ConnectionProfile? = null
    private var reconnectJob: Job? = null
    @Volatile
    private var manualDisconnect = false
    @Volatile
    private var reconnectAttempt = 0

    fun connect(profile: ConnectionProfile) {
        manualDisconnect = false
        currentProfile = profile
        reconnectAttempt = 0
        reconnectJob?.cancel()
        openSocket(profile)
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        val sock = activeSocket
        activeSocket = null
        sock?.close(1000, "User disconnected")
        failAllPending("Disconnected")
        _connectionState.value = BridgeConnectionState.DISCONNECTED
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private fun openSocket(profile: ConnectionProfile) {
        val prev = activeSocket
        activeSocket = null
        prev?.cancel()
        _connectionState.value = BridgeConnectionState.CONNECTING

        val req = Request.Builder()
            .url(profile.acpWebSocketUrl())
            .apply {
                if (profile.token.isNotBlank()) {
                    header("Authorization", "Bearer ${profile.token.trim()}")
                }
            }
            .build()

        val ws = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== activeSocket) return
                reconnectAttempt = 0
                _connectionState.value = BridgeConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== activeSocket) return
                runCatching { handleIncoming(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== activeSocket) return
                activeSocket = null
                failAllPending("Connection closed: $reason")
                if (!manualDisconnect) {
                    scheduleReconnect(profile)
                } else {
                    _connectionState.value = BridgeConnectionState.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== activeSocket) return
                activeSocket = null
                failAllPending(t.message ?: "WebSocket failure")
                _connectionState.value = BridgeConnectionState.ERROR
                val httpCode = response?.code
                if (httpCode == 401 || httpCode == 403) {
                    scope.launch {
                        _events.emit(AcpClientEvent.BridgeNotice("Bridge authentication failed (HTTP $httpCode). Check your Bearer token."))
                    }
                    return
                }
                if (!manualDisconnect) {
                    scheduleReconnect(profile)
                }
            }
        })
        activeSocket = ws
    }

    private fun scheduleReconnect(profile: ConnectionProfile) {
        reconnectJob?.cancel()
        val attempt = reconnectAttempt++
        reconnectJob = scope.launch {
            val baseMs = (1000L * (1L shl attempt.coerceAtMost(4))).coerceAtMost(15_000L)
            val jitterMs = (Math.random() * 500.0).toLong()
            delay(baseMs + jitterMs)
            if (!manualDisconnect && currentProfile?.id == profile.id) {
                openSocket(profile)
            }
        }
    }

    private fun handleIncoming(raw: String) {
        val envelope = runCatching { json.decodeFromString<JsonRpcEnvelope>(raw) }.getOrNull() ?: return

        // 1. Response to an outgoing client request
        if (envelope.id != null && envelope.method == null) {
            val key = (envelope.id as? JsonPrimitive)?.contentOrNull ?: envelope.id.toString()
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

        // 2. Server -> Client request requiring a response
        if (envelope.id != null && envelope.method == "session/request_permission" && envelope.params != null) {
            val parsed = AcpParser.parsePermissionRequest(envelope.id, envelope.params)
            scope.launch { _events.emit(AcpClientEvent.PermissionRequested(parsed)) }
            return
        }
        if (envelope.id != null && envelope.method == "elicitation/create" && envelope.params != null) {
            val parsed = AcpParser.parseElicitationRequest(envelope.id, envelope.params)
            scope.launch { _events.emit(AcpClientEvent.ElicitationRequested(parsed)) }
            return
        }

        // 3. Notifications
        when (envelope.method) {
            "session/update" -> {
                val params = envelope.params ?: return
                AcpParser.parseSessionUpdate(params)?.let { ev ->
                    scope.launch { _events.emit(AcpClientEvent.SessionUpdate(ev)) }
                }
            }
            "bridge/error", "bridge/agy_exited" -> {
                val msg = (envelope.params?.get("message") as? JsonPrimitive)?.contentOrNull
                    ?: "agy process exited (${envelope.params?.get("code") ?: "?"})"
                scope.launch { _events.emit(AcpClientEvent.BridgeNotice(msg)) }
            }
        }
    }

    suspend fun sendRequest(
        method: String,
        params: JsonObject,
        timeoutMs: Long = 120_000L
    ): JsonElement {
        val socket = activeSocket ?: throw IllegalStateException("Not connected to agy-bridge")
        val idNum = nextId.getAndIncrement()
        val idKey = idNum.toString()
        val deferred = CompletableDeferred<JsonElement>()
        pending[idKey] = deferred

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", JsonPrimitive(idNum))
            put("method", method)
            put("params", params)
        }.toString()

        if (!socket.send(payload)) {
            pending.remove(idKey)
            throw IllegalStateException("Failed to enqueue WebSocket message")
        }

        return try {
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
        activeSocket?.send(payload)
    }

    fun sendPermissionReply(rpcId: JsonElement, optionId: String?) {
        val reply = AcpParser.buildPermissionResponse(rpcId, optionId).toString()
        activeSocket?.send(reply)
    }

    fun sendElicitationReply(rpcId: JsonElement, action: String, content: Map<String, String> = emptyMap()) {
        val reply = AcpParser.buildElicitationResponse(rpcId, action, content).toString()
        activeSocket?.send(reply)
    }

    private fun failAllPending(reason: String) {
        val keys = pending.keys.toList()
        for (k in keys) {
            pending.remove(k)?.completeExceptionally(IllegalStateException(reason))
        }
    }
}
