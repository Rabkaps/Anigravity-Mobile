package com.hambalapps.agymobile.ui.chat

import com.hambalapps.agymobile.data.acp.AcpConfigOption
import com.hambalapps.agymobile.data.acp.AcpElicitationRequest
import com.hambalapps.agymobile.data.acp.AcpInitializeResult
import com.hambalapps.agymobile.data.acp.AcpPermissionRequest
import com.hambalapps.agymobile.data.acp.AcpSubagentInfo
import com.hambalapps.agymobile.data.acp.DiffFileEntry
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.data.acp.ModelQuotaInfo
import com.hambalapps.agymobile.data.acp.QuotaSummaryResponse
import com.hambalapps.agymobile.data.acp.SessionSummary
import com.hambalapps.agymobile.data.engine.GoogleLoginState
import com.hambalapps.agymobile.data.engine.InstalledPluginInfo
import com.hambalapps.agymobile.data.engine.MarketplaceCatalogItem
import com.hambalapps.agymobile.data.engine.SkillItemInfo
import com.hambalapps.agymobile.data.local.AppPreferences
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import java.util.UUID

enum class MessageRole {
    USER,
    AGENT,
    SYSTEM
}

data class ToolCallUi(
    val toolCallId: String,
    val title: String,
    val kind: String,
    val status: String,
    val textOutput: String = "",
    val diffPath: String? = null,
    val diffOld: String? = null,
    val diffNew: String? = null,
    val imageBase64: String? = null
)

data class ArtifactUi(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val markdown: String,
    val approved: Boolean? = null
)

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String = "",
    val thinking: String = "",
    val toolCalls: List<ToolCallUi> = emptyList(),
    val planSteps: List<String> = emptyList(),
    val isStreaming: Boolean = false
)

data class QueuedMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val text: String
)

enum class ActiveSheet {
    NONE,
    AUTH,
    MODE_MODEL,
    DIFF,
    ARTIFACT,
    AGENTS_MCP,
    TERMINAL,
    SETTINGS
}

data class ChatUiState(
    val preferences: AppPreferences? = null,
    val connectionState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val health: HealthResponse? = null,
    val initializeResult: AcpInitializeResult? = null,
    val activeSubagents: List<AcpSubagentInfo> = emptyList(),
    val authRequired: Boolean = false,
    val authInProgress: Boolean = false,
    val authError: String? = null,
    val statusBanner: String? = null,
    val pendingBrowserUrl: String? = null,
    val sessionId: String? = null,
    val workspaceCwd: String = "/data/data/com.termux/files/home",
    val extraDirs: List<String> = emptyList(),
    val messages: List<ChatMessageUi> = emptyList(),
    val composerText: String = "",
    val queuedMessages: List<QueuedMessageUi> = emptyList(),
    val isStreaming: Boolean = false,
    val configOptions: List<AcpConfigOption> = emptyList(),
    val usedTokens: Int = 0,
    val maxTokens: Int = 1_000_000,
    val permissionQueue: List<AcpPermissionRequest> = emptyList(),
    val elicitationQueue: List<AcpElicitationRequest> = emptyList(),
    val dynamicSlashCommands: List<SlashCommandSpec> = emptyList(),
    val savedSessions: List<SessionSummary> = emptyList(),
    val diffFiles: List<DiffFileEntry> = emptyList(),
    val artifacts: List<ArtifactUi> = emptyList(),
    val selectedArtifactId: String? = null,
    val bridgeSettingsJson: String = "{}",
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val terminalPendingCommand: String? = null,
    val openedTerminalForAuth: Boolean = false,
    val voiceListening: Boolean = false,
    val connectionTestResult: String? = null,
    val isBootstrapping: Boolean = false,
    val bootstrapStatus: String? = null,
    val termuxInstalled: Boolean = false,
    val hasTermuxPermission: Boolean = false,
    val quotaSummary: QuotaSummaryResponse? = null,
    val isLoadingQuota: Boolean = false,
    val standaloneInstalled: Boolean = false,
    val standaloneToolchainInstalled: Boolean = false,
    val standaloneSignedIn: Boolean = false,
    val standaloneAgyVersion: String? = null,
    val standaloneProgress: Float? = null,
    val standaloneStatus: String? = null,
    val detectedAbi: String = "",
    val googleLoginState: GoogleLoginState = GoogleLoginState(),
    val marketplaceCatalog: List<MarketplaceCatalogItem> = emptyList(),
    val installedPlugins: List<InstalledPluginInfo> = emptyList(),
    val isLoadingPlugins: Boolean = false,
    val pluginActionTarget: String? = null,
    val pluginStatusMessage: String? = null,
    val globalRulesText: String = "",
    val workspaceRulesText: String = "",
    val discoveredSkills: List<SkillItemInfo> = emptyList()
) {
    val activeProfile: ConnectionProfile?
        get() = preferences?.activeProfile

    val modeOption: AcpConfigOption?
        get() = configOptions.firstOrNull { it.category == "mode" || it.id == "mode" }

    val modelOption: AcpConfigOption?
        get() = configOptions.firstOrNull { it.category == "model" || it.id == "model" }

    val thoughtOption: AcpConfigOption?
        get() = configOptions.firstOrNull {
            it.category == "thought_level" ||
                it.id.contains("thought", ignoreCase = true) ||
                it.id.contains("effort", ignoreCase = true)
        }

    val activeModeLabel: String
        get() = modeOption?.currentDisplayLabel() ?: "default"

    val activeModelLabel: String
        get() = modelOption?.currentDisplayLabel() ?: "connect to load"

    val activeEffortLabel: String
        get() = thoughtOption?.currentDisplayLabel() ?: ""

    val contextUsageRatio: Float
        get() = if (maxTokens <= 0) 0f else (usedTokens.toFloat() / maxTokens.toFloat()).coerceIn(0f, 1f)

    val currentPermission: AcpPermissionRequest?
        get() = permissionQueue.firstOrNull()

    val currentElicitation: AcpElicitationRequest?
        get() = elicitationQueue.firstOrNull()

    val runningTasks: List<ToolCallUi>
        get() = messages.flatMap { it.toolCalls }.filter {
            it.status.lowercase() !in setOf("completed", "failed", "error", "cancelled", "done")
        }

    fun quotaForModelChoice(choiceValue: String, choiceName: String = ""): ModelQuotaInfo? {
        val summary = quotaSummary ?: return null
        val models = summary.models
        if (models.isEmpty() && summary.buckets.isEmpty()) return null

        val lower = choiceValue.lowercase()
        val stripped = lower.replace(Regex("-(minimal|low|medium|high|xhigh|none|tiered|sonar)$"), "")
        val direct = models[choiceValue]
            ?: models[lower]
            ?: models[stripped]
            ?: models.values.firstOrNull { entry ->
                val dn = entry.displayName?.lowercase().orEmpty()
                val cn = choiceName.lowercase()
                (dn.isNotBlank() && cn.isNotBlank() && (dn == cn || dn.startsWith(cn) || cn.startsWith(dn))) ||
                    entry.modelId.lowercase().startsWith(stripped)
            }
            ?: if (lower.startsWith("gemini")) {
                models["__gemini_pool__"] ?: models.values.firstOrNull {
                    it.modelId.startsWith("gemini-", ignoreCase = true) && it.remainingFraction != null
                }
            } else null

        if (direct != null) {
            if (direct.remainingFraction != null || direct.quotaBucketName.isNullOrBlank()) {
                return direct
            }
            val bucket = summary.buckets.firstOrNull {
                it.displayName.equals(direct.quotaBucketName, ignoreCase = true) ||
                    it.bucketId.equals(direct.quotaBucketName, ignoreCase = true)
            }
            if (bucket != null) {
                return direct.copy(
                    remainingFraction = bucket.remainingFraction,
                    resetTime = direct.resetTime ?: bucket.resetTime,
                    disabled = direct.disabled || bucket.disabled
                )
            }
            return direct
        }

        val matchingBucket = summary.buckets.firstOrNull { b ->
            val bName = b.displayName.lowercase()
            val cName = choiceName.lowercase()
            bName.isNotBlank() && cName.isNotBlank() && (cName.contains(bName) || bName.contains(cName))
        }
        return matchingBucket?.let {
            ModelQuotaInfo(
                modelId = choiceValue,
                displayName = it.displayName,
                remainingFraction = it.remainingFraction,
                resetTime = it.resetTime,
                quotaBucketName = it.displayName,
                disabled = it.disabled
            )
        }
    }
}
