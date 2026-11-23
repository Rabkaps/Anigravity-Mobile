package com.hambalapps.agymobile.ui.chat

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hambalapps.agymobile.data.acp.AcpAuthMethod
import com.hambalapps.agymobile.data.acp.AcpClientEvent
import com.hambalapps.agymobile.data.acp.AcpElicitationRequest
import com.hambalapps.agymobile.data.acp.AcpParser
import com.hambalapps.agymobile.data.acp.AcpPermissionRequest
import com.hambalapps.agymobile.data.acp.AcpRpcException
import com.hambalapps.agymobile.data.acp.AcpSessionEvent
import com.hambalapps.agymobile.data.acp.AcpWebSocketClient
import com.hambalapps.agymobile.data.acp.BridgeApiClient
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.data.acp.PtyWebSocketClient
import com.hambalapps.agymobile.data.engine.LocalAcpClient
import com.hambalapps.agymobile.data.engine.QuotaClient
import com.hambalapps.agymobile.data.engine.ShizukuBridge
import com.hambalapps.agymobile.data.engine.StandaloneEngineManager
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.data.local.SettingsRepository
import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val acpClient = AcpWebSocketClient()
    val engineManager = StandaloneEngineManager(application)
    private val localAcpClient = LocalAcpClient(engineManager)
    private val quotaClient = QuotaClient()
    private val bridgeApi = BridgeApiClient()
    val ptyClient = PtyWebSocketClient()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            standaloneInstalled = engineManager.isAgyInstalled(),
            standaloneToolchainInstalled = engineManager.isToolchainInstalled(),
            detectedAbi = engineManager.detectArch().abi
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    var onRequestVoiceInput: (() -> Unit)? = null
    var onRequestTermuxPermission: (() -> Unit)? = null

    private var lastConnectedProfileKey: String? = null
    private var activeAgentMessageId: String? = null
    private var activeUserMessageId: String? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            viewModelScope.launch {
                ShizukuBridge.warmUp(getApplication())
                val priv = engineManager.detectPrivilegeLevel()
                _uiState.update { state ->
                    state.copy(health = state.health?.copy(privilegeLevel = priv))
                }
            }
        }
    }

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        ShizukuBridge.requestPermissionIfNeeded()
    }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        }
        ShizukuBridge.requestPermissionIfNeeded()
        refreshTermuxStatus()
        refreshStandaloneStatus()
        observePreferences()
        observeConnectionState()
        observeAcpEvents()
    }

    private fun isStandaloneActive(): Boolean = _uiState.value.activeProfile?.isStandalone == true

    private suspend fun acpRequest(
        method: String,
        params: JsonObject,
        timeoutMs: Long = 120_000L
    ): JsonElement = if (isStandaloneActive()) {
        localAcpClient.sendRequest(method, params, timeoutMs)
    } else {
        acpClient.sendRequest(method, params, timeoutMs)
    }

    private fun acpNotify(method: String, params: JsonObject) {
        if (isStandaloneActive()) localAcpClient.sendNotification(method, params)
        else acpClient.sendNotification(method, params)
    }

    private fun acpPermissionReply(rpcId: JsonElement, optionId: String?) {
        if (isStandaloneActive()) localAcpClient.sendPermissionReply(rpcId, optionId)
        else acpClient.sendPermissionReply(rpcId, optionId)
    }

    private fun acpElicitationReply(rpcId: JsonElement, action: String, content: Map<String, String>) {
        if (isStandaloneActive()) localAcpClient.sendElicitationReply(rpcId, action, content)
        else acpClient.sendElicitationReply(rpcId, action, content)
    }

    private fun refreshStandaloneStatus() {
        _uiState.update {
            it.copy(
                standaloneInstalled = engineManager.isAgyInstalled(),
                standaloneToolchainInstalled = engineManager.isToolchainInstalled(),
                detectedAbi = engineManager.detectArch().abi
            )
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            settingsRepo.preferencesFlow.collect { prefs ->
                val profile = prefs.activeProfile
                _uiState.update { state ->
                    state.copy(
                        preferences = prefs,
                        workspaceCwd = if (state.workspaceCwd.isBlank() || state.workspaceCwd == "/data/data/com.termux/files/home" && profile.isStandalone) {
                            profile.defaultCwd
                        } else {
                            state.workspaceCwd
                        }
                    )
                }

                val key = "${profile.id}:${profile.isStandalone}:${profile.host}:${profile.port}:${profile.token}:${profile.useTls}"
                if (key != lastConnectedProfileKey) {
                    lastConnectedProfileKey = key
                    connectToProfile(profile)
                }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            acpClient.connectionState.collect { connState ->
                if (!isStandaloneActive()) {
                    _uiState.update { it.copy(connectionState = connState) }
                    if (connState == BridgeConnectionState.CONNECTED) {
                        initializeAcpSession()
                    }
                }
            }
        }
        viewModelScope.launch {
            localAcpClient.connectionState.collect { connState ->
                if (isStandaloneActive()) {
                    _uiState.update { it.copy(connectionState = connState) }
                    if (connState == BridgeConnectionState.CONNECTED) {
                        initializeAcpSession()
                    }
                }
            }
        }
    }

    private fun observeAcpEvents() {
        viewModelScope.launch {
            acpClient.events.collect { event ->
                if (!isStandaloneActive()) dispatchAcpEvent(event)
            }
        }
        viewModelScope.launch {
            localAcpClient.events.collect { event ->
                if (isStandaloneActive()) dispatchAcpEvent(event)
            }
        }
    }

    private fun dispatchAcpEvent(event: AcpClientEvent) {
        when (event) {
            is AcpClientEvent.SessionUpdate -> handleSessionUpdate(event.event)
            is AcpClientEvent.PermissionRequested -> {
                val policy = _uiState.value.preferences?.permissionPolicy ?: "request_review"
                val kindLower = event.request.kind.lowercase()
                val autoOptionId = event.request.options.firstOrNull {
                    it.kind == "allow_always" || it.kind == "allow_once" || !it.kind.startsWith("reject")
                }?.optionId
                val shouldAutoApprove = when (policy) {
                    "turbo" -> autoOptionId != null
                    "auto" -> autoOptionId != null && kindLower in setOf("read", "search", "list", "view", "fetch")
                    else -> false
                }
                if (shouldAutoApprove) {
                    acpPermissionReply(event.request.rpcId, autoOptionId)
                } else {
                    _uiState.update {
                        it.copy(permissionQueue = it.permissionQueue + event.request)
                    }
                }
            }
            is AcpClientEvent.ElicitationRequested -> {
                _uiState.update {
                    it.copy(elicitationQueue = it.elicitationQueue + event.request)
                }
            }
            is AcpClientEvent.BridgeNotice -> {
                _uiState.update { it.copy(statusBanner = event.message) }
            }
        }
    }

    fun reconnect() {
        val profile = _uiState.value.activeProfile ?: return
        connectToProfile(profile)
    }

    private fun connectToProfile(profile: ConnectionProfile) {
        _uiState.update { it.copy(statusBanner = null, workspaceCwd = profile.defaultCwd.ifBlank { engineManager.homeDir.absolutePath }) }
        if (profile.isStandalone) {
            acpClient.disconnect()
            refreshStandaloneStatus()
            viewModelScope.launch {
                if (engineManager.isAgyInstalled()) {
                    engineManager.migrateTermuxCredentialsIfNeeded()
                    val priv = engineManager.detectPrivilegeLevel()
                    val arch = engineManager.detectArch()
                    _uiState.update {
                        it.copy(
                            health = HealthResponse(
                                ok = true,
                                bridgeVersion = "standalone",
                                agyVersion = engineManager.installedAgyVersion(),
                                platform = "android",
                                arch = arch.abi,
                                isTermux = false,
                                privilegeLevel = priv,
                                defaultWorkspace = engineManager.homeDir.absolutePath
                            ),
                            workspaceCwd = profile.defaultCwd.ifBlank { engineManager.homeDir.absolutePath }
                        )
                    }
                    localAcpClient.start(profile.defaultCwd.ifBlank { engineManager.homeDir.absolutePath })
                } else {
                    localAcpClient.disconnect()
                    _uiState.update {
                        it.copy(
                            connectionState = BridgeConnectionState.DISCONNECTED,
                            health = null
                        )
                    }
                }
            }
            return
        }

        localAcpClient.disconnect()
        viewModelScope.launch {
            bridgeApi.getHealth(profile)
                .onSuccess { health ->
                    _uiState.update {
                        it.copy(
                            health = health,
                            workspaceCwd = health.defaultWorkspace.ifBlank { profile.defaultCwd }
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            health = null,
                            statusBanner = "Bridge unreachable (${err.message}). Start agy-bridge or switch to Standalone Engine."
                        )
                    }
                }
        }
        acpClient.connect(profile)
    }

    private fun initializeAcpSession() {
        viewModelScope.launch {
            runCatching {
                val initRaw = acpRequest(
                    method = "initialize",
                    params = buildJsonObject {
                        put("protocolVersion", 1)
                        put("clientCapabilities", buildJsonObject {
                            put("auth", buildJsonObject {
                                put("terminal", true)
                            })
                            put("elicitation", buildJsonObject {
                                put("form", buildJsonObject { })
                                put("url", buildJsonObject { })
                            })
                            put("fs", buildJsonObject {
                                put("readTextFile", true)
                                put("writeTextFile", true)
                            })
                        })
                        put("clientInfo", buildJsonObject {
                            put("name", "Android Studio")
                            put("title", "Agy Mobile (Android Studio ACP)")
                            put("version", "1.3.0")
                        })
                    }
                )
                val initResult = AcpParser.parseInitializeResult(initRaw)
                _uiState.update { it.copy(initializeResult = initResult) }
                createNewSession()
            }.onFailure { err ->
                val isAuthErr = (err is AcpRpcException && err.code == -32000) ||
                    (err.message?.contains("auth", ignoreCase = true) == true) ||
                    (err.message?.contains("login", ignoreCase = true) == true)
                if (isAuthErr) {
                    _uiState.update {
                        it.copy(
                            authRequired = true,
                            authError = null,
                            activeSheet = ActiveSheet.AUTH
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBanner = "ACP handshake failed: ${err.message}")
                    }
                }
            }
        }
    }

    private suspend fun createNewSession(cwdOverride: String? = null) {
        val cwd = cwdOverride ?: _uiState.value.workspaceCwd
        val result = acpRequest(
            method = "session/new",
            params = buildJsonObject {
                put("cwd", cwd)
                put("mcpServers", JsonArray(emptyList()))
            }
        )
        val parsed = AcpParser.parseSessionSetupResult(result)
        if (parsed.sessionId.isNotBlank()) {
            activeUserMessageId = null
            _uiState.update {
                it.copy(
                    sessionId = parsed.sessionId,
                    configOptions = parsed.configOptions,
                    activeSubagents = emptyList(),
                    authRequired = false,
                    authInProgress = false,
                    authError = null,
                    statusBanner = null
                )
            }
            val savedModel = settingsRepo.preferencesFlow.first().selectedModel
            if (savedModel.isNotBlank() && savedModel != "default") {
                val modelOpt = parsed.configOptions.firstOrNull {
                    it.isLegacyModel || it.category == "model" || it.id == "model"
                }
                if (modelOpt != null &&
                    modelOpt.currentValue != savedModel &&
                    modelOpt.options.any { it.value == savedModel }
                ) {
                    setConfigOption(modelOpt.id, savedModel)
                }
            }
            refreshQuota()
        }
    }

    fun authenticateWithMethod(method: AcpAuthMethod) {
        viewModelScope.launch {
            _uiState.update { it.copy(authInProgress = true, authError = null) }
            runCatching {
                acpRequest(
                    method = "authenticate",
                    params = buildJsonObject {
                        put("methodId", method.id)
                    },
                    timeoutMs = 180_000L
                )
                createNewSession()
                _uiState.update {
                    it.copy(
                        authRequired = false,
                        authInProgress = false,
                        authError = null,
                        activeSheet = ActiveSheet.NONE
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        authInProgress = false,
                        authError = err.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun submitGeminiApiKey(apiKey: String) {
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(authInProgress = true, authError = null) }
            if (profile.isStandalone) {
                localAcpClient.setEnvOverrides(mapOf("GEMINI_API_KEY" to apiKey))
                _uiState.update {
                    it.copy(
                        authInProgress = false,
                        authRequired = false,
                        activeSheet = ActiveSheet.NONE
                    )
                }
                localAcpClient.start(_uiState.value.workspaceCwd)
                return@launch
            }
            bridgeApi.setEnvAndRestart(profile, buildJsonObject { put("GEMINI_API_KEY", apiKey) })
                .onSuccess {
                    delay(800)
                    _uiState.update {
                        it.copy(
                            authInProgress = false,
                            authRequired = false,
                            activeSheet = ActiveSheet.NONE
                        )
                    }
                    acpClient.connect(profile)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(authInProgress = false, authError = "Failed to set key: ${err.message}")
                    }
                }
        }
    }

    fun openTerminalForAuth(command: String) {
        val profile = _uiState.value.activeProfile
        if (profile != null) {
            if (profile.isStandalone) ptyClient.connectLocal(engineManager, _uiState.value.workspaceCwd)
            else ptyClient.connect(profile)
        }
        _uiState.update {
            it.copy(
                terminalPendingCommand = command,
                openedTerminalForAuth = true,
                activeSheet = ActiveSheet.TERMINAL
            )
        }
    }

    fun finishTerminalAuthAndReconnect() {
        ptyClient.disconnect()
        _uiState.update {
            it.copy(
                terminalPendingCommand = null,
                openedTerminalForAuth = false,
                activeSheet = ActiveSheet.NONE
            )
        }
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            if (profile.isStandalone) {
                localAcpClient.start(_uiState.value.workspaceCwd)
            } else {
                bridgeApi.restartAgy(profile)
                delay(600)
                acpClient.connect(profile)
            }
        }
    }

    fun clearTerminalPendingCommand() {
        _uiState.update { it.copy(terminalPendingCommand = null) }
    }

    fun openUrlInBrowser(url: String, force: Boolean = false) {
        engineManager.openUrlInBrowser(url, force)
    }

    fun retryAcpHandshake() {
        _uiState.update { it.copy(authError = null) }
        initializeAcpSession()
    }

    fun respondElicitation(request: AcpElicitationRequest, action: String, content: Map<String, String>) {
        acpElicitationReply(request.rpcId, action, content)
        _uiState.update { state ->
            state.copy(elicitationQueue = state.elicitationQueue.filterNot { it.rpcId == request.rpcId })
        }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun appendVoiceResult(spoken: String) {
        val current = _uiState.value.composerText
        val joined = if (current.isBlank()) spoken else "$current $spoken"
        _uiState.update { it.copy(composerText = joined, voiceListening = false) }
    }

    fun setVoiceListening(listening: Boolean) {
        _uiState.update { it.copy(voiceListening = listening) }
    }

    fun onSlashCommandPicked(spec: SlashCommandSpec) {
        when (spec.target) {
            SlashCommandTarget.LOCAL_SHEET -> {
                _uiState.update { it.copy(composerText = "") }
                spec.sheetTrigger?.let { openSheetForTrigger(it) }
            }
            SlashCommandTarget.LOCAL_ACTION -> {
                _uiState.update { it.copy(composerText = "") }
                executeLocalAction(spec.command)
            }
            SlashCommandTarget.AGENT_PROMPT -> {
                val suffix = if (spec.argHint != null) " " else ""
                _uiState.update { it.copy(composerText = "${spec.command}$suffix") }
            }
        }
    }

    private fun openSheetForTrigger(trigger: LocalSheetTrigger) {
        when (trigger) {
            LocalSheetTrigger.MODE_MODEL -> openSheet(ActiveSheet.MODE_MODEL)
            LocalSheetTrigger.DIFF -> {
                refreshDiff()
                openSheet(ActiveSheet.DIFF)
            }
            LocalSheetTrigger.ARTIFACT -> openSheet(ActiveSheet.ARTIFACT)
            LocalSheetTrigger.AGENTS_MCP -> {
                refreshConfig()
                openSheet(ActiveSheet.AGENTS_MCP)
            }
            LocalSheetTrigger.SESSIONS -> refreshSessions()
            LocalSheetTrigger.TERMINAL -> openTerminalSheet()
            LocalSheetTrigger.SETTINGS -> openSheet(ActiveSheet.SETTINGS)
        }
    }

    private fun executeLocalAction(rawCommand: String) {
        val cmd = rawCommand.trim().substringBefore(' ').lowercase()
        when (cmd) {
            "/clear", "/new" -> startFreshChat()
            "/fork", "/branch" -> forkConversation()
            "/voice" -> onRequestVoiceInput?.invoke()
            "/logout" -> logoutFromAgent()
            "/add-dir" -> {
                val arg = rawCommand.trim().substringAfter(' ', "").trim()
                if (arg.isNotEmpty()) {
                    addWorkspaceDir(arg)
                }
            }
        }
    }

    fun sendPrompt(overridePrompt: String? = null) {
        val raw = (overridePrompt ?: _uiState.value.composerText).trim()
        if (raw.isEmpty() || _uiState.value.isStreaming) return

        // Handle local slash commands typed manually and submitted
        if (raw.startsWith("/")) {
            val head = raw.substringBefore(' ').lowercase()
            val matched = SlashCommandCatalog.builtIn.firstOrNull {
                it.command == head || it.aliases.contains(head)
            }
            if (matched != null && matched.target != SlashCommandTarget.AGENT_PROMPT) {
                if (overridePrompt == null) _uiState.update { it.copy(composerText = "") }
                if (matched.target == SlashCommandTarget.LOCAL_SHEET) {
                    matched.sheetTrigger?.let { openSheetForTrigger(it) }
                } else {
                    executeLocalAction(raw)
                }
                return
            }
        }

        val sessionId = _uiState.value.sessionId
        if (sessionId == null) {
            _uiState.update { it.copy(statusBanner = "No active ACP session yet. Connect to agy-bridge first.") }
            return
        }

        val userMsg = ChatMessageUi(role = MessageRole.USER, text = raw)
        val agentMsg = ChatMessageUi(role = MessageRole.AGENT, isStreaming = true)
        activeAgentMessageId = agentMsg.id

        _uiState.update { state ->
            state.copy(
                composerText = if (overridePrompt == null) "" else state.composerText,
                messages = state.messages + userMsg + agentMsg,
                isStreaming = true,
                statusBanner = null
            )
        }

        viewModelScope.launch {
            runCatching {
                acpRequest(
                    method = "session/prompt",
                    params = buildJsonObject {
                        put("sessionId", sessionId)
                        put("prompt", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", raw)
                            })
                        })
                    },
                    timeoutMs = 300_000L
                )
            }.onSuccess {
                finalizeAgentTurn()
            }.onFailure { err ->
                appendAgentError(err.message ?: "Prompt failed")
            }
        }
    }

    fun cancelTurn() {
        val sessionId = _uiState.value.sessionId ?: return
        acpNotify(
            method = "session/cancel",
            params = buildJsonObject { put("sessionId", sessionId) }
        )
        finalizeAgentTurn()
    }

    fun respondPermission(request: AcpPermissionRequest, optionId: String?) {
        acpPermissionReply(request.rpcId, optionId)
        _uiState.update { state ->
            state.copy(permissionQueue = state.permissionQueue.filterNot { it.rpcId == request.rpcId })
        }
    }

    fun startFreshChat() {
        activeAgentMessageId = null
        activeUserMessageId = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                activeSubagents = emptyList(),
                isStreaming = false,
                usedTokens = 0,
                statusBanner = null
            )
        }
        viewModelScope.launch {
            runCatching { createNewSession() }
        }
    }

    fun forkConversation() {
        val transcript = _uiState.value.messages
            .takeLast(8)
            .joinToString("\n\n") { m ->
                "${if (m.role == MessageRole.USER) "User" else "Assistant"}: ${m.text.take(600)}"
            }
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessageUi(
                        role = MessageRole.SYSTEM,
                        text = "Branched conversation into a new parallel session."
                    )
                ),
                isStreaming = false
            )
        }
        viewModelScope.launch {
            runCatching {
                createNewSession()
                if (transcript.isNotBlank()) {
                    sendPrompt("/compact Continuing in branched thread. Prior context:\n$transcript")
                }
            }
        }
    }

    fun loadSavedSession(sessionId: String) {
        val cwd = _uiState.value.workspaceCwd
        activeAgentMessageId = null
        activeUserMessageId = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                activeSubagents = emptyList(),
                isStreaming = false,
                statusBanner = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val res = acpRequest(
                    method = "session/load",
                    params = buildJsonObject {
                        put("sessionId", sessionId)
                        put("cwd", cwd)
                        put("mcpServers", JsonArray(emptyList()))
                    }
                )
                val parsed = AcpParser.parseSessionSetupResult(res)
                activeAgentMessageId = null
                activeUserMessageId = null
                _uiState.update { state ->
                    val finalizedMessages = state.messages
                        .map { msg -> if (msg.isStreaming) msg.copy(isStreaming = false) else msg }
                        .ifEmpty {
                            listOf(
                                ChatMessageUi(
                                    role = MessageRole.SYSTEM,
                                    text = "Resumed session $sessionId"
                                )
                            )
                        }
                    state.copy(
                        sessionId = parsed.sessionId.ifBlank { sessionId },
                        configOptions = if (parsed.configOptions.isNotEmpty()) parsed.configOptions else state.configOptions,
                        messages = finalizedMessages,
                        isStreaming = false
                    )
                }
                refreshQuota()
            }.onFailure { err ->
                activeAgentMessageId = null
                activeUserMessageId = null
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        statusBanner = "Could not load session: ${err.message}"
                    )
                }
            }
        }
    }

    fun setPermissionPolicy(policy: String) {
        viewModelScope.launch {
            settingsRepo.setPermissionPolicy(policy)
            if (policy == "turbo") {
                val queued = _uiState.value.permissionQueue
                if (queued.isNotEmpty()) {
                    _uiState.update { it.copy(permissionQueue = emptyList()) }
                    queued.forEach { req ->
                        val optId = req.options.firstOrNull {
                            it.kind == "allow_always" || it.kind == "allow_once" || !it.kind.startsWith("reject")
                        }?.optionId
                        acpPermissionReply(req.rpcId, optId)
                    }
                }
            }
        }
    }

    fun setConfigOption(configId: String, value: String) {
        val sessionId = _uiState.value.sessionId ?: return
        val currentOpt = _uiState.value.configOptions.firstOrNull { it.id == configId }

        viewModelScope.launch {
            runCatching {
                when {
                    currentOpt?.isLegacyMode == true -> {
                        acpRequest(
                            method = "session/set_mode",
                            params = buildJsonObject {
                                put("sessionId", sessionId)
                                put("modeId", value)
                            }
                        )
                        _uiState.update { state ->
                            state.copy(
                                configOptions = state.configOptions.map {
                                    if (it.id == configId) it.copy(currentValue = value) else it
                                }
                            )
                        }
                    }
                    currentOpt?.isLegacyModel == true -> {
                        acpRequest(
                            method = "session/set_model",
                            params = buildJsonObject {
                                put("sessionId", sessionId)
                                put("modelId", value)
                            }
                        )
                        settingsRepo.setSelectedModel(value)
                        _uiState.update { state ->
                            state.copy(
                                configOptions = state.configOptions.map {
                                    if (it.id == configId) it.copy(currentValue = value) else it
                                }
                            )
                        }
                    }
                    else -> {
                        val resp = acpRequest(
                            method = "session/set_config_option",
                            params = buildJsonObject {
                                put("sessionId", sessionId)
                                put("configId", configId)
                                put("value", value)
                            }
                        )
                        if (currentOpt?.category == "model" || configId == "model") {
                            settingsRepo.setSelectedModel(value)
                        }
                        val updated = AcpParser.parseConfigOptionsArray(resp.jsonObject["configOptions"])
                        _uiState.update { state ->
                            state.copy(
                                configOptions = if (updated.isNotEmpty()) {
                                    updated
                                } else {
                                    state.configOptions.map {
                                        if (it.id == configId) it.copy(currentValue = value) else it
                                    }
                                }
                            )
                        }
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBanner = "Could not update option: ${err.message}") }
            }
        }
    }

    fun logoutFromAgent() {
        val supportsLogout = _uiState.value.initializeResult?.supportsLogout == true
        if (!supportsLogout) {
            openTerminalForAuth("agy /logout")
            return
        }
        viewModelScope.launch {
            runCatching {
                acpRequest(method = "logout", params = buildJsonObject { })
            }
            _uiState.update {
                it.copy(
                    sessionId = null,
                    configOptions = emptyList(),
                    authRequired = true,
                    activeSheet = ActiveSheet.AUTH
                )
            }
        }
    }

    fun changeWorkspaceCwd(newCwd: String) {
        val trimmed = newCwd.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(workspaceCwd = trimmed) }
        viewModelScope.launch {
            runCatching { createNewSession(cwdOverride = trimmed) }
        }
    }

    fun addWorkspaceDir(dirPath: String) {
        _uiState.update { state ->
            if (dirPath in state.extraDirs) state
            else state.copy(
                extraDirs = state.extraDirs + dirPath,
                messages = state.messages + ChatMessageUi(
                    role = MessageRole.SYSTEM,
                    text = "Added workspace directory: $dirPath"
                )
            )
        }
    }

    fun openSheet(sheet: ActiveSheet) {
        if (sheet == ActiveSheet.MODE_MODEL) refreshQuota()
        if (sheet == ActiveSheet.DIFF) refreshDiff()
        if (sheet == ActiveSheet.AGENTS_MCP) refreshConfig()
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun closeSheet() {
        if (_uiState.value.activeSheet == ActiveSheet.TERMINAL) {
            ptyClient.disconnect()
        }
        _uiState.update {
            it.copy(
                activeSheet = ActiveSheet.NONE,
                terminalPendingCommand = null,
                openedTerminalForAuth = false
            )
        }
    }

    fun openTerminalSheet() {
        val profile = _uiState.value.activeProfile
        if (profile != null) {
            if (profile.isStandalone) ptyClient.connectLocal(engineManager, _uiState.value.workspaceCwd)
            else ptyClient.connect(profile)
        }
        _uiState.update {
            it.copy(
                terminalPendingCommand = null,
                openedTerminalForAuth = false,
                activeSheet = ActiveSheet.TERMINAL
            )
        }
    }

    fun openTerminalWithCommand(command: String) {
        val profile = _uiState.value.activeProfile
        if (profile != null) {
            if (profile.isStandalone) ptyClient.connectLocal(engineManager, _uiState.value.workspaceCwd)
            else ptyClient.connect(profile)
        }
        _uiState.update {
            it.copy(
                terminalPendingCommand = command,
                openedTerminalForAuth = false,
                activeSheet = ActiveSheet.TERMINAL
            )
        }
    }

    fun refreshQuota() {
        val profile = _uiState.value.activeProfile ?: return
        _uiState.update { it.copy(isLoadingQuota = true) }
        viewModelScope.launch {
            if (profile.isStandalone) {
                val res = quotaClient.fetchQuota(engineManager.homeDir)
                _uiState.update { it.copy(quotaSummary = res, isLoadingQuota = false) }
            } else {
                bridgeApi.getQuota(profile)
                    .onSuccess { res ->
                        _uiState.update { it.copy(quotaSummary = res, isLoadingQuota = false) }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isLoadingQuota = false,
                                quotaSummary = it.quotaSummary ?: com.hambalapps.agymobile.data.acp.QuotaSummaryResponse(
                                    ok = false,
                                    error = "Quota unavailable (${err.message}). Re-bootstrap bridge if needed."
                                )
                            )
                        }
                    }
            }
        }
    }

    fun refreshDiff() {
        val profile = _uiState.value.activeProfile ?: return
        if (profile.isStandalone) return
        viewModelScope.launch {
            bridgeApi.getDiff(profile, _uiState.value.workspaceCwd).onSuccess { resp ->
                _uiState.update { it.copy(diffFiles = resp.files) }
            }
        }
    }

    fun refreshSessions() {
        val profile = _uiState.value.activeProfile ?: return
        val supportsList = _uiState.value.initializeResult?.supportsSessionList == true
        viewModelScope.launch {
            if (supportsList) {
                val acpRes = runCatching {
                    acpRequest(
                        method = "session/list",
                        params = buildJsonObject {
                            put("cwd", _uiState.value.workspaceCwd)
                        }
                    )
                }
                if (acpRes.isSuccess) {
                    val list = AcpParser.parseSessionList(acpRes.getOrThrow())
                    _uiState.update { it.copy(savedSessions = list) }
                    return@launch
                }
            }
            if (!profile.isStandalone) {
                bridgeApi.getSessions(profile).onSuccess { list ->
                    _uiState.update { it.copy(savedSessions = list) }
                }
            }
        }
    }

    fun refreshConfig() {
        val profile = _uiState.value.activeProfile ?: return
        if (profile.isStandalone) {
            val settingsFile = java.io.File(engineManager.homeDir, ".gemini/antigravity-cli/settings.json")
            val text = if (settingsFile.exists()) runCatching { settingsFile.readText() }.getOrDefault("{}") else "{}"
            _uiState.update { it.copy(bridgeSettingsJson = text) }
            return
        }
        viewModelScope.launch {
            bridgeApi.getConfig(profile).onSuccess { cfg ->
                _uiState.update { it.copy(bridgeSettingsJson = cfg.toString()) }
            }
        }
    }

    fun approveArtifact(artifactId: String, approved: Boolean) {
        _uiState.update { state ->
            state.copy(
                artifacts = state.artifacts.map {
                    if (it.id == artifactId) it.copy(approved = approved) else it
                }
            )
        }
        val label = if (approved) "Approved artifact" else "Requested changes on artifact"
        sendPrompt("$label. Please proceed accordingly.")
        closeSheet()
    }

    fun saveProfile(profile: ConnectionProfile, setActive: Boolean = true) {
        viewModelScope.launch { settingsRepo.saveProfile(profile, setActive) }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch { settingsRepo.deleteProfile(profileId) }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch { settingsRepo.setActiveProfile(profileId) }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAmoledDark(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun testConnection(profile: ConnectionProfile) {
        if (profile.isStandalone) {
            val arch = engineManager.detectArch()
            val ver = engineManager.installedAgyVersion() ?: "not installed yet"
            val tc = if (engineManager.isToolchainInstalled()) "installed" else "not installed"
            _uiState.update {
                it.copy(connectionTestResult = "Standalone (${arch.abi}): agy=$ver, git/node=$tc")
            }
            return
        }
        _uiState.update { it.copy(connectionTestResult = "Testing ${profile.httpBaseUrl()}…") }
        viewModelScope.launch {
            bridgeApi.getHealth(profile)
                .onSuccess { h ->
                    _uiState.update {
                        it.copy(
                            connectionTestResult =
                                "Connected! agy=${h.agyVersion ?: "not found"}, privilege=${h.privilegeLevel}, host=${h.platform}/${h.arch}"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(connectionTestResult = "Failed: ${err.message}")
                    }
                }
        }
    }

    fun startStandaloneEngine(includeToolchain: Boolean = false) {
        if (_uiState.value.isBootstrapping) return
        viewModelScope.launch {
            val standaloneProfile = _uiState.value.preferences?.profiles
                ?.firstOrNull { it.isStandalone || it.id == "local-standalone" }
                ?: ConnectionProfile(
                    id = "local-standalone",
                    name = "Standalone Engine (Built-in)",
                    host = "127.0.0.1",
                    port = 0,
                    isStandalone = true,
                    defaultCwd = engineManager.homeDir.absolutePath
                )
            settingsRepo.saveProfile(standaloneProfile, setActive = true)

            _uiState.update {
                it.copy(
                    isBootstrapping = true,
                    standaloneProgress = 0.02f,
                    standaloneStatus = "Preparing built-in Antigravity engine (${engineManager.detectArch().abi})…",
                    bootstrapStatus = "Preparing standalone engine…"
                )
            }

            val agyRes = engineManager.installOrUpdateAgy { msg, frac ->
                val scaled = if (includeToolchain) frac * 0.4f else frac
                _uiState.update {
                    it.copy(
                        standaloneProgress = scaled,
                        standaloneStatus = msg,
                        bootstrapStatus = msg
                    )
                }
            }
            if (agyRes.isFailure) {
                val errMsg = agyRes.exceptionOrNull()?.message ?: "Download failed"
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        standaloneProgress = null,
                        standaloneStatus = "Standalone setup failed: $errMsg",
                        bootstrapStatus = "Standalone setup failed: $errMsg"
                    )
                }
                return@launch
            }

            if (includeToolchain && !engineManager.isToolchainInstalled()) {
                val tcRes = engineManager.installPortableToolchain { msg, frac ->
                    _uiState.update {
                        it.copy(
                            standaloneProgress = 0.4f + frac * 0.6f,
                            standaloneStatus = msg,
                            bootstrapStatus = msg
                        )
                    }
                }
                if (tcRes.isFailure) {
                    val errMsg = tcRes.exceptionOrNull()?.message ?: "Toolchain install failed"
                    _uiState.update {
                        it.copy(
                            standaloneStatus = "agy ready, toolchain note: $errMsg",
                            bootstrapStatus = "agy ready, toolchain note: $errMsg"
                        )
                    }
                }
            }

            refreshStandaloneStatus()
            val priv = engineManager.detectPrivilegeLevel()
            val arch = engineManager.detectArch()
            _uiState.update {
                it.copy(
                    isBootstrapping = false,
                    standaloneProgress = null,
                    health = HealthResponse(
                        ok = true,
                        bridgeVersion = "standalone",
                        agyVersion = engineManager.installedAgyVersion(),
                        platform = "android",
                        arch = arch.abi,
                        isTermux = false,
                        privilegeLevel = priv,
                        defaultWorkspace = engineManager.homeDir.absolutePath
                    ),
                    standaloneStatus = "Standalone engine online (${arch.abi}, privilege=$priv)",
                    bootstrapStatus = "Standalone engine online (${arch.abi}, privilege=$priv)",
                    statusBanner = null
                )
            }
            localAcpClient.start(engineManager.homeDir.absolutePath)
        }
    }

    fun installStandaloneToolchain() {
        if (_uiState.value.isBootstrapping) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBootstrapping = true,
                    standaloneProgress = 0.02f,
                    standaloneStatus = "Installing portable Linux + git + node/npm (${engineManager.detectArch().abi})…",
                    bootstrapStatus = "Installing portable Linux toolchain…"
                )
            }
            val tcRes = engineManager.installPortableToolchain { msg, frac ->
                _uiState.update {
                    it.copy(
                        standaloneProgress = frac,
                        standaloneStatus = msg,
                        bootstrapStatus = msg
                    )
                }
            }
            refreshStandaloneStatus()
            _uiState.update {
                it.copy(
                    isBootstrapping = false,
                    standaloneProgress = null,
                    standaloneStatus = if (tcRes.isSuccess) {
                        "Portable Linux toolchain (bash, git, node, npm) ready!"
                    } else {
                        "Toolchain failed: ${tcRes.exceptionOrNull()?.message}"
                    },
                    bootstrapStatus = if (tcRes.isSuccess) {
                        "Portable Linux toolchain ready!"
                    } else {
                        "Toolchain failed: ${tcRes.exceptionOrNull()?.message}"
                    }
                )
            }
            if (tcRes.isSuccess && isStandaloneActive()) {
                localAcpClient.start(_uiState.value.workspaceCwd)
            }
        }
    }

    private fun copyAndOpenTermux(ctx: Application, command: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("Agy Bridge Setup", command))
        TermuxBridgeBootstrap.openTermuxApp(ctx)
    }

    fun refreshTermuxStatus() {
        val ctx = getApplication<Application>()
        _uiState.update {
            it.copy(
                termuxInstalled = TermuxBridgeBootstrap.isTermuxInstalled(ctx),
                hasTermuxPermission = TermuxBridgeBootstrap.hasRunCommandPermission(ctx)
            )
        }
    }

    fun onTermuxPermissionResult(granted: Boolean) {
        refreshTermuxStatus()
        if (granted) {
            autoSetupAndStartBridge(useRootSu = false)
        } else {
            _uiState.update {
                it.copy(
                    isBootstrapping = false,
                    bootstrapStatus = "Termux permission denied. Grant it in App Settings or use Zero-Touch Root (su)."
                )
            }
        }
    }

    fun autoSetupAndStartBridge(useRootSu: Boolean = false) {
        if (_uiState.value.isBootstrapping) return
        val ctx = getApplication<Application>()
        refreshTermuxStatus()

        if (!TermuxBridgeBootstrap.isTermuxInstalled(ctx)) {
            _uiState.update {
                it.copy(bootstrapStatus = "Termux (com.termux) is not installed on this device.")
            }
            return
        }

        val runCommandSupported = TermuxBridgeBootstrap.isRunCommandSupported(ctx)
        if (!useRootSu && runCommandSupported && !TermuxBridgeBootstrap.hasRunCommandPermission(ctx)) {
            _uiState.update {
                it.copy(bootstrapStatus = "Requesting Termux RUN_COMMAND permission…")
            }
            onRequestTermuxPermission?.invoke()
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBootstrapping = true,
                    bootstrapStatus = "Extracting bundled agy-bridge & generating pairing token…"
                )
            }

            val prefs = _uiState.value.preferences
            val baseLocal = prefs?.profiles?.firstOrNull { !it.isRemote && !it.isStandalone } ?: ConnectionProfile(
                id = "local-termux",
                name = "On-Device Termux",
                host = "127.0.0.1",
                port = 4789,
                token = "",
                useTls = false,
                isRemote = false,
                isStandalone = false
            )
            val ensuredToken = baseLocal.token.ifBlank { TermuxBridgeBootstrap.generateSecureToken() }
            val updatedProfile = baseLocal.copy(token = ensuredToken)
            settingsRepo.saveProfile(updatedProfile, setActive = true)

            val stagedResult = runCatching {
                TermuxBridgeBootstrap.extractBundledBridge(ctx, ensuredToken, updatedProfile.port)
            }
            val stagedBridge = stagedResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        bootstrapStatus = "Failed to unpack bridge assets: ${err.message}"
                    )
                }
                return@launch
            }

            if (useRootSu) {
                _uiState.update {
                    it.copy(bootstrapStatus = "Bootstrapping Termux bridge via root (su -t 0)…")
                }
                val rootResult = TermuxBridgeBootstrap.launchViaRootSu(
                    staged = stagedBridge,
                    token = ensuredToken,
                    port = updatedProfile.port
                )
                val rootMsg = rootResult.exceptionOrNull()?.message.orEmpty()
                if (rootResult.isFailure) {
                    if (rootMsg.contains("NEEDS_TERMUX_FIRST_INSTALL")) {
                        val inlineCmd = TermuxBridgeBootstrap.buildSelfExtractingTermuxCommand(
                            stagedBridge.files,
                            ensuredToken,
                            updatedProfile.port
                        )
                        copyAndOpenTermux(ctx, inlineCmd)
                        _uiState.update {
                            it.copy(
                                bootstrapStatus = "Files staged! Termux needs 1-time nodejs-lts install — setup command copied to clipboard. Paste & press Enter in Termux; waiting for bridge…"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isBootstrapping = false,
                                bootstrapStatus = "Root bootstrap failed: $rootMsg"
                            )
                        }
                        return@launch
                    }
                }
            } else if (!runCommandSupported) {
                val inlineCmd = TermuxBridgeBootstrap.buildSelfExtractingTermuxCommand(
                    stagedBridge.files,
                    ensuredToken,
                    updatedProfile.port
                )
                copyAndOpenTermux(ctx, inlineCmd)
                _uiState.update {
                    it.copy(
                        bootstrapStatus = "Google Play Termux doesn't support RUN_COMMAND. Setup command copied to clipboard — paste & press Enter in Termux; waiting for bridge…"
                    )
                }
            } else {
                _uiState.update { it.copy(bootstrapStatus = "Sending setup command to Termux…") }
                val launchResult = TermuxBridgeBootstrap.launchViaTermuxRunCommand(
                    context = ctx,
                    staged = stagedBridge,
                    token = ensuredToken,
                    port = updatedProfile.port,
                    background = true
                )
                if (launchResult.isFailure) {
                    val inlineCmd = TermuxBridgeBootstrap.buildSelfExtractingTermuxCommand(
                        stagedBridge.files,
                        ensuredToken,
                        updatedProfile.port
                    )
                    copyAndOpenTermux(ctx, inlineCmd)
                    _uiState.update {
                        it.copy(
                            bootstrapStatus = "Termux rejected background intent. Setup command copied to clipboard — paste & press Enter in Termux; waiting for bridge…"
                        )
                    }
                }
            }

            _uiState.update { it.copy(bootstrapStatus = "Waiting for agy-bridge to respond on :${updatedProfile.port}…") }
            var healthy = false
            repeat(40) {
                delay(1500)
                val check = bridgeApi.getHealth(updatedProfile)
                if (check.isSuccess) {
                    val health = check.getOrNull()
                    healthy = true
                    _uiState.update {
                        it.copy(
                            isBootstrapping = false,
                            health = health,
                            statusBanner = null,
                            bootstrapStatus = if (health?.agyVersion != null) {
                                "Bridge online! agy=${health.agyVersion}, privilege=${health.privilegeLevel}"
                            } else {
                                "Bridge online! Install `agy` in Termux or tap Login / Terminal to sign in."
                            }
                        )
                    }
                    lastConnectedProfileKey = "${updatedProfile.id}:${updatedProfile.host}:${updatedProfile.port}:${updatedProfile.token}:${updatedProfile.useTls}"
                    acpClient.connect(updatedProfile)
                    return@launch
                }
            }

            if (!healthy) {
                _uiState.update {
                    it.copy(
                        isBootstrapping = false,
                        bootstrapStatus = "Timed out waiting for bridge. On unrooted phones, ensure `allow-external-apps=true` is set in Termux (first install of nodejs-lts can also take ~30s)."
                    )
                }
            }
        }
    }

    private fun handleSessionUpdate(event: AcpSessionEvent) {
        when (event) {
            is AcpSessionEvent.UserMessageChunk -> {
                val prevAgentId = activeAgentMessageId
                activeAgentMessageId = null
                _uiState.update { state ->
                    val settled = if (prevAgentId != null) {
                        state.messages.map { m -> if (m.id == prevAgentId) m.copy(isStreaming = false) else m }
                    } else {
                        state.messages
                    }
                    val targetUserId = activeUserMessageId
                    val updatedMessages = if (targetUserId == null) {
                        val created = ChatMessageUi(role = MessageRole.USER, text = event.text)
                        activeUserMessageId = created.id
                        settled + created
                    } else {
                        settled.map { msg ->
                            if (msg.id == targetUserId) msg.copy(text = msg.text + event.text) else msg
                        }
                    }
                    state.copy(messages = updatedMessages, isStreaming = false)
                }
            }
            is AcpSessionEvent.AgentMessageChunk -> {
                activeUserMessageId = null
                updateActiveAgentMessage { msg ->
                    val newText = msg.text + event.text
                    msg.copy(text = newText, isStreaming = true)
                }
                maybeCaptureArtifact()
            }
            is AcpSessionEvent.AgentThoughtChunk -> {
                activeUserMessageId = null
                updateActiveAgentMessage { msg ->
                    msg.copy(thinking = msg.thinking + event.text, isStreaming = true)
                }
            }
            is AcpSessionEvent.ToolCallEvent -> {
                activeUserMessageId = null
                updateActiveAgentMessage { msg ->
                    val updatedList = msg.toolCalls.toMutableList()
                    val idx = updatedList.indexOfFirst { it.toolCallId == event.toolCallId }
                    val newTool = ToolCallUi(
                        toolCallId = event.toolCallId,
                        title = event.title,
                        kind = event.kind,
                        status = event.status,
                        textOutput = event.textOutput,
                        diffPath = event.diffPath,
                        diffOld = event.diffOld,
                        diffNew = event.diffNew,
                        imageBase64 = event.imageBase64
                    )
                    if (idx >= 0) {
                        val prev = updatedList[idx]
                        updatedList[idx] = newTool.copy(
                            title = newTool.title.ifBlank { prev.title },
                            textOutput = newTool.textOutput.ifBlank { prev.textOutput },
                            imageBase64 = newTool.imageBase64 ?: prev.imageBase64,
                            diffPath = newTool.diffPath ?: prev.diffPath,
                            diffOld = newTool.diffOld ?: prev.diffOld,
                            diffNew = newTool.diffNew ?: prev.diffNew
                        )
                    } else {
                        updatedList.add(newTool)
                    }
                    msg.copy(toolCalls = updatedList, isStreaming = true)
                }
            }
            is AcpSessionEvent.PlanUpdate -> {
                activeUserMessageId = null
                updateActiveAgentMessage { msg ->
                    msg.copy(planSteps = event.entries, isStreaming = true)
                }
            }
            is AcpSessionEvent.SubagentUpdate -> {
                _uiState.update { state ->
                    val merged = state.activeSubagents.toMutableList()
                    event.subagents.forEach { incoming ->
                        val idx = merged.indexOfFirst { it.id == incoming.id }
                        if (idx >= 0) {
                            val existing = merged[idx]
                            merged[idx] = existing.copy(
                                name = incoming.name.ifBlank { existing.name },
                                role = incoming.role.ifBlank { existing.role },
                                status = incoming.status.ifBlank { existing.status }
                            )
                        } else {
                            merged.add(incoming)
                        }
                    }
                    state.copy(activeSubagents = merged)
                }
            }
            is AcpSessionEvent.ModeUpdate -> {
                _uiState.update { state ->
                    state.copy(
                        configOptions = state.configOptions.map { opt ->
                            if (opt.category == "mode" || opt.id == "mode") {
                                opt.copy(currentValue = event.modeId)
                            } else opt
                        }
                    )
                }
            }
            is AcpSessionEvent.ConfigOptionsUpdate -> {
                _uiState.update { it.copy(configOptions = event.configOptions) }
            }
            is AcpSessionEvent.AvailableCommandsUpdate -> {
                val mapped = event.commands.map { c ->
                    val slashName = if (c.name.startsWith("/")) c.name else "/${c.name}"
                    SlashCommandSpec(
                        command = slashName,
                        category = "Agent",
                        description = c.description,
                        target = SlashCommandTarget.AGENT_PROMPT
                    )
                }
                _uiState.update { it.copy(dynamicSlashCommands = mapped) }
            }
            is AcpSessionEvent.UsageUpdate -> {
                _uiState.update {
                    it.copy(usedTokens = event.usedTokens, maxTokens = event.maxTokens)
                }
            }
        }
    }

    private fun updateActiveAgentMessage(transform: (ChatMessageUi) -> ChatMessageUi) {
        _uiState.update { state ->
            val targetId = activeAgentMessageId
            if (targetId == null) {
                val created = transform(ChatMessageUi(role = MessageRole.AGENT, isStreaming = true))
                activeAgentMessageId = created.id
                state.copy(messages = state.messages + created, isStreaming = true)
            } else {
                state.copy(
                    messages = state.messages.map { msg ->
                        if (msg.id == targetId) transform(msg) else msg
                    }
                )
            }
        }
    }

    private fun maybeCaptureArtifact() {
        val targetId = activeAgentMessageId ?: return
        val activeMsg = _uiState.value.messages.firstOrNull { it.id == targetId } ?: return
        if (activeMsg.text.contains("## Implementation Plan") || activeMsg.text.contains("# Walkthrough")) {
            val title = if (activeMsg.text.contains("# Walkthrough")) "Walkthrough" else "Implementation Plan"
            _uiState.update { state ->
                val existing = state.artifacts.firstOrNull { it.id == targetId }
                val updatedArtifacts = if (existing != null) {
                    state.artifacts.map {
                        if (it.id == targetId) it.copy(title = title, markdown = activeMsg.text) else it
                    }
                } else {
                    state.artifacts + ArtifactUi(id = targetId, title = title, markdown = activeMsg.text)
                }
                state.copy(artifacts = updatedArtifacts, selectedArtifactId = targetId)
            }
        }
    }

    private fun finalizeAgentTurn() {
        val targetId = activeAgentMessageId
        activeAgentMessageId = null
        _uiState.update { state ->
            state.copy(
                isStreaming = false,
                messages = state.messages.map { msg ->
                    if (msg.id == targetId) msg.copy(isStreaming = false) else msg
                }
            )
        }
        refreshDiff()
        refreshQuota()
    }

    private fun appendAgentError(errorText: String) {
        val targetId = activeAgentMessageId
        activeAgentMessageId = null
        _uiState.update { state ->
            state.copy(
                isStreaming = false,
                messages = state.messages.map { msg ->
                    if (msg.id == targetId) {
                        msg.copy(
                            text = (msg.text + "\n\n**Error:** $errorText").trim(),
                            isStreaming = false
                        )
                    } else msg
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        }
        ptyClient.disconnect()
        acpClient.disconnect()
        localAcpClient.disconnect()
    }
}
