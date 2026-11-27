package com.hambalapps.agymobile.data.acp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

class AcpRpcException(val code: Int, override val message: String) : IllegalStateException(message)

@Serializable
data class JsonRpcErrorPayload(
    val code: Int = -1,
    val message: String = "Unknown error",
    val data: JsonElement? = null
)

@Serializable
data class JsonRpcEnvelope(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String? = null,
    val params: JsonObject? = null,
    val result: JsonElement? = null,
    val error: JsonRpcErrorPayload? = null
)

@Serializable
data class AcpPermissionOption(
    val optionId: String,
    val name: String,
    val kind: String = "allow_once"
)

@Serializable
data class AcpPermissionRequest(
    val rpcId: JsonElement,
    val sessionId: String,
    val toolCallId: String,
    val title: String,
    val kind: String = "other",
    val rawInputPreview: String = "",
    val options: List<AcpPermissionOption> = emptyList()
)

@Serializable
data class AvailableCommand(
    val name: String,
    val description: String = ""
)

data class AcpAuthMethod(
    val id: String,
    val name: String,
    val description: String = "",
    val type: String = "agent",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

data class AcpInitializeResult(
    val protocolVersion: Int = 1,
    val agentName: String = "Antigravity ACP Server",
    val agentVersion: String = "",
    val supportsLoadSession: Boolean = false,
    val supportsSessionList: Boolean = false,
    val supportsSessionResume: Boolean = false,
    val supportsLogout: Boolean = false,
    val authMethods: List<AcpAuthMethod> = emptyList()
)

data class AcpConfigChoice(
    val value: String,
    val name: String,
    val description: String = "",
    val group: String? = null
)

data class AcpConfigOption(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val type: String = "select",
    val currentValue: String = "",
    val options: List<AcpConfigChoice> = emptyList(),
    val isLegacyMode: Boolean = false,
    val isLegacyModel: Boolean = false
) {
    fun currentDisplayLabel(): String =
        options.firstOrNull { it.value == currentValue }?.name
            ?: currentValue.ifBlank { "Default" }
}

data class AcpSessionSetupResult(
    val sessionId: String,
    val configOptions: List<AcpConfigOption>
)

data class AcpElicitationField(
    val key: String,
    val title: String,
    val description: String = "",
    val type: String = "string",
    val enumOptions: List<String> = emptyList(),
    val defaultValue: String = "",
    val required: Boolean = false
)

data class AcpElicitationRequest(
    val rpcId: JsonElement,
    val sessionId: String,
    val mode: String,
    val message: String,
    val url: String? = null,
    val fields: List<AcpElicitationField> = emptyList()
)

data class AcpSubagentInfo(
    val id: String,
    val name: String,
    val role: String = "",
    val status: String = "active"
)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val bridgeVersion: String = "",
    val agyVersion: String? = null,
    val platform: String = "",
    val arch: String = "",
    val isTermux: Boolean = false,
    val privilegeLevel: String = "none",
    val defaultWorkspace: String = ""
)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String,
    val cwd: String = "",
    val updatedAt: String = ""
)

@Serializable
data class DiffFileEntry(
    val path: String,
    val status: String = "modified",
    val diff: String = ""
)

@Serializable
data class DiffResponse(
    val cwd: String = "",
    val files: List<DiffFileEntry> = emptyList()
)

@Serializable
data class ModelQuotaInfo(
    val modelId: String,
    val displayName: String? = null,
    val remainingFraction: Double? = null,
    val resetTime: String? = null,
    val quotaBucketName: String? = null,
    val disabled: Boolean = false
)

@Serializable
data class QuotaBucketInfo(
    val bucketId: String,
    val displayName: String,
    val description: String? = null,
    val window: String? = null,
    val remainingFraction: Double? = null,
    val remainingAmount: Double? = null,
    val resetTime: String? = null,
    val disabled: Boolean = false
)

@Serializable
data class QuotaSummaryResponse(
    val ok: Boolean = false,
    val email: String? = null,
    val authMethod: String? = null,
    val buckets: List<QuotaBucketInfo> = emptyList(),
    val models: Map<String, ModelQuotaInfo> = emptyMap(),
    val error: String? = null
)

sealed interface AcpSessionEvent {
    val sessionId: String
    data class UserMessageChunk(override val sessionId: String, val text: String) : AcpSessionEvent
    data class AgentMessageChunk(override val sessionId: String, val text: String) : AcpSessionEvent
    data class AgentThoughtChunk(override val sessionId: String, val text: String) : AcpSessionEvent
    data class SubagentUpdate(override val sessionId: String, val subagents: List<AcpSubagentInfo>) : AcpSessionEvent
    data class ToolCallEvent(
        override val sessionId: String,
        val toolCallId: String,
        val title: String,
        val kind: String,
        val status: String,
        val textOutput: String = "",
        val diffPath: String? = null,
        val diffOld: String? = null,
        val diffNew: String? = null,
        val imageBase64: String? = null
    ) : AcpSessionEvent
    data class PlanUpdate(override val sessionId: String, val entries: List<String>) : AcpSessionEvent
    data class ModeUpdate(override val sessionId: String, val modeId: String) : AcpSessionEvent
    data class ConfigOptionsUpdate(
        override val sessionId: String,
        val configOptions: List<AcpConfigOption>
    ) : AcpSessionEvent
    data class AvailableCommandsUpdate(
        override val sessionId: String,
        val commands: List<AvailableCommand>
    ) : AcpSessionEvent
    data class UsageUpdate(
        override val sessionId: String,
        val usedTokens: Int,
        val maxTokens: Int
    ) : AcpSessionEvent
}

object AcpParser {

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement?.asPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    fun parseInitializeResult(result: JsonElement): AcpInitializeResult {
        val obj = result.asObjectOrNull() ?: return AcpInitializeResult()
        val protocolVersion = obj["protocolVersion"]?.asPrimitiveOrNull()?.intOrNull ?: 1
        val agentInfo = obj["agentInfo"]?.asObjectOrNull()
        val agentName = agentInfo?.get("title")?.asPrimitiveOrNull()?.contentOrNull
            ?: agentInfo?.get("name")?.asPrimitiveOrNull()?.contentOrNull
            ?: "Antigravity ACP Server"
        val agentVersion = agentInfo?.get("version")?.asPrimitiveOrNull()?.contentOrNull ?: ""

        val caps = obj["agentCapabilities"]?.asObjectOrNull()
        val loadSession = caps?.get("loadSession")?.asPrimitiveOrNull()?.booleanOrNull ?: false
        val sessionCaps = caps?.get("sessionCapabilities")?.asObjectOrNull()
        val supportsList = sessionCaps?.get("list") != null
        val supportsResume = sessionCaps?.get("resume") != null
        val supportsLogout = caps?.get("auth")?.asObjectOrNull()?.get("logout") != null

        val authMethods = obj["authMethods"]?.asArrayOrNull()
            ?.mapNotNull { elem ->
                val m = elem.asObjectOrNull() ?: return@mapNotNull null
                val id = m["id"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                val name = m["name"]?.asPrimitiveOrNull()?.contentOrNull ?: id
                val desc = m["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                val type = m["type"]?.asPrimitiveOrNull()?.contentOrNull ?: "agent"
                val args = m["args"]?.asArrayOrNull()
                    ?.mapNotNull { it.asPrimitiveOrNull()?.contentOrNull }
                    .orEmpty()
                val env = m["env"]?.asObjectOrNull()
                    ?.mapValues { it.value.asPrimitiveOrNull()?.contentOrNull.orEmpty() }
                    .orEmpty()
                AcpAuthMethod(
                    id = id,
                    name = name,
                    description = desc,
                    type = type,
                    args = args,
                    env = env
                )
            }.orEmpty()

        return AcpInitializeResult(
            protocolVersion = protocolVersion,
            agentName = agentName,
            agentVersion = agentVersion,
            supportsLoadSession = loadSession,
            supportsSessionList = supportsList,
            supportsSessionResume = supportsResume,
            supportsLogout = supportsLogout,
            authMethods = authMethods
        )
    }

    fun parseSessionSetupResult(result: JsonElement): AcpSessionSetupResult {
        val obj = result.asObjectOrNull()
            ?: return AcpSessionSetupResult("", emptyList())
        val sessionId = obj["sessionId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""

        val parsedOptions = parseConfigOptionsArray(obj["configOptions"]).toMutableList()

        // Backwards compatibility: synthesize mode/model options if server omitted configOptions
        val hasMode = parsedOptions.any { it.category == "mode" || it.id == "mode" }
        if (!hasMode) {
            obj["modes"]?.asObjectOrNull()?.let { modesObj ->
                val current = modesObj["currentModeId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                val available = modesObj["availableModes"]?.asArrayOrNull()
                    ?.mapNotNull { elem ->
                        val m = elem.asObjectOrNull() ?: return@mapNotNull null
                        val id = m["id"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                        val name = m["name"]?.asPrimitiveOrNull()?.contentOrNull ?: id
                        val desc = m["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                        AcpConfigChoice(value = id, name = name, description = desc)
                    }.orEmpty()
                if (available.isNotEmpty()) {
                    parsedOptions.add(
                        AcpConfigOption(
                            id = "__legacy_mode__",
                            name = "Execution Mode",
                            category = "mode",
                            currentValue = current,
                            options = available,
                            isLegacyMode = true
                        )
                    )
                }
            }
        }

        val hasModel = parsedOptions.any { it.category == "model" || it.id == "model" }
        if (!hasModel) {
            obj["models"]?.asObjectOrNull()?.let { modelsObj ->
                val current = modelsObj["currentModelId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                val available = modelsObj["availableModels"]?.asArrayOrNull()
                    ?.mapNotNull { elem ->
                        val m = elem.asObjectOrNull() ?: return@mapNotNull null
                        val id = m["modelId"]?.asPrimitiveOrNull()?.contentOrNull
                            ?: m["id"]?.asPrimitiveOrNull()?.contentOrNull
                            ?: return@mapNotNull null
                        val name = m["name"]?.asPrimitiveOrNull()?.contentOrNull ?: id
                        val desc = m["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                        AcpConfigChoice(value = id, name = name, description = desc)
                    }.orEmpty()
                if (available.isNotEmpty()) {
                    parsedOptions.add(
                        AcpConfigOption(
                            id = "__legacy_model__",
                            name = "Model",
                            category = "model",
                            currentValue = current,
                            options = available,
                            isLegacyModel = true
                        )
                    )
                }
            }
        }

        return AcpSessionSetupResult(sessionId = sessionId, configOptions = parsedOptions)
    }

    fun parseConfigOptionsArray(elem: JsonElement?): List<AcpConfigOption> {
        val arr = elem.asArrayOrNull() ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.asPrimitiveOrNull()?.contentOrNull ?: id
            val description = obj["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            val category = obj["category"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            val type = obj["type"]?.asPrimitiveOrNull()?.contentOrNull ?: "select"
            val currentValue = obj["currentValue"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            val choices = parseConfigChoices(obj["options"])

            AcpConfigOption(
                id = id,
                name = name,
                description = description,
                category = category,
                type = type,
                currentValue = currentValue,
                options = choices
            )
        }
    }

    private fun parseConfigChoices(elem: JsonElement?): List<AcpConfigChoice> {
        val arr = elem.asArrayOrNull() ?: return emptyList()
        val choices = mutableListOf<AcpConfigChoice>()
        for (entry in arr) {
            val obj = entry.asObjectOrNull() ?: continue
            val groupOptions = obj["options"]?.asArrayOrNull()
            if (groupOptions != null) {
                val groupName = obj["name"]?.asPrimitiveOrNull()?.contentOrNull
                    ?: obj["group"]?.asPrimitiveOrNull()?.contentOrNull
                for (sub in groupOptions) {
                    val subObj = sub.asObjectOrNull() ?: continue
                    val value = subObj["value"]?.asPrimitiveOrNull()?.contentOrNull ?: continue
                    val name = subObj["name"]?.asPrimitiveOrNull()?.contentOrNull ?: value
                    val desc = subObj["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                    choices.add(AcpConfigChoice(value = value, name = name, description = desc, group = groupName))
                }
            } else {
                val value = obj["value"]?.asPrimitiveOrNull()?.contentOrNull ?: continue
                val name = obj["name"]?.asPrimitiveOrNull()?.contentOrNull ?: value
                val desc = obj["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                choices.add(AcpConfigChoice(value = value, name = name, description = desc))
            }
        }
        return choices
    }

    fun parseSessionList(result: JsonElement): List<SessionSummary> {
        val obj = result.asObjectOrNull() ?: return emptyList()
        val arr = obj["sessions"]?.asArrayOrNull() ?: return emptyList()
        return arr.mapNotNull { elem ->
            val s = elem.asObjectOrNull() ?: return@mapNotNull null
            val id = s["sessionId"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
            val title = s["title"]?.asPrimitiveOrNull()?.contentOrNull?.ifBlank { id } ?: id
            val cwd = s["cwd"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            val updatedAt = s["updatedAt"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            SessionSummary(id = id, title = title, cwd = cwd, updatedAt = updatedAt)
        }
    }

    fun parseElicitationRequest(rpcId: JsonElement, params: JsonObject): AcpElicitationRequest {
        val sessionId = params["sessionId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
        val mode = params["mode"]?.asPrimitiveOrNull()?.contentOrNull ?: "form"
        val message = params["message"]?.asPrimitiveOrNull()?.contentOrNull ?: "Agent requested input"
        val url = params["url"]?.asPrimitiveOrNull()?.contentOrNull

        val schemaObj = params["requestedSchema"]?.asObjectOrNull()
        val requiredKeys = schemaObj?.get("required")?.asArrayOrNull()
            ?.mapNotNull { it.asPrimitiveOrNull()?.contentOrNull }
            ?.toSet()
            .orEmpty()
        val properties = schemaObj?.get("properties")?.asObjectOrNull()

        val fields = properties?.entries?.mapNotNull { (key, rawDef) ->
            val def = rawDef.asObjectOrNull() ?: return@mapNotNull null
            val title = def["title"]?.asPrimitiveOrNull()?.contentOrNull ?: key
            val desc = def["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            val type = def["type"]?.asPrimitiveOrNull()?.contentOrNull ?: "string"
            val enumOpts = def["enum"]?.asArrayOrNull()
                ?.mapNotNull { it.asPrimitiveOrNull()?.contentOrNull }
                .orEmpty()
            val defVal = def["default"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
            AcpElicitationField(
                key = key,
                title = title,
                description = desc,
                type = type,
                enumOptions = enumOpts,
                defaultValue = defVal,
                required = key in requiredKeys
            )
        }.orEmpty()

        return AcpElicitationRequest(
            rpcId = rpcId,
            sessionId = sessionId,
            mode = mode,
            message = message,
            url = url,
            fields = fields
        )
    }

    fun buildElicitationResponse(
        rpcId: JsonElement,
        action: String,
        content: Map<String, String> = emptyMap()
    ): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", rpcId)
        put("result", buildJsonObject {
            put("action", action)
            if (action == "accept" && content.isNotEmpty()) {
                put("content", buildJsonObject {
                    content.forEach { (k, v) -> put(k, v) }
                })
            }
        })
    }

    fun parseSessionUpdate(params: JsonObject): AcpSessionEvent? {
        val sessionId = params["sessionId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
        val update = params["update"]?.asObjectOrNull() ?: return null
        val kind = update["sessionUpdate"]?.asPrimitiveOrNull()?.contentOrNull ?: return null

        return when (kind) {
            "user_message_chunk" -> {
                val text = extractChunkText(update["content"])
                if (text != null) AcpSessionEvent.UserMessageChunk(sessionId, text) else null
            }
            "agent_message_chunk" -> {
                val text = extractChunkText(update["content"])
                if (text != null) AcpSessionEvent.AgentMessageChunk(sessionId, text) else null
            }
            "agent_thought_chunk" -> {
                val text = extractChunkText(update["content"])
                if (text != null) AcpSessionEvent.AgentThoughtChunk(sessionId, text) else null
            }
            "subagent_update", "subagents_update" -> parseSubagentUpdate(sessionId, update)
            "tool_call", "tool_call_update" -> parseToolCall(sessionId, update)
            "current_mode_update" -> {
                val modeId = update["currentModeId"]?.asPrimitiveOrNull()?.contentOrNull ?: return null
                AcpSessionEvent.ModeUpdate(sessionId, modeId)
            }
            "config_option_update" -> {
                val opts = parseConfigOptionsArray(update["configOptions"])
                AcpSessionEvent.ConfigOptionsUpdate(sessionId, opts)
            }
            "available_commands_update" -> {
                val arr = update["availableCommands"]?.asArrayOrNull() ?: return null
                val cmds = arr.mapNotNull { elem ->
                    val obj = elem.asObjectOrNull() ?: return@mapNotNull null
                    val name = obj["name"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                    val desc = obj["description"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
                    AvailableCommand(name = name, description = desc)
                }
                AcpSessionEvent.AvailableCommandsUpdate(sessionId, cmds)
            }
            "plan" -> {
                val arr = update["entries"]?.asArrayOrNull() ?: return null
                val items = arr.mapNotNull { entry ->
                    val obj = entry.asObjectOrNull() ?: return@mapNotNull null
                    val content = obj["content"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
                    val status = obj["status"]?.asPrimitiveOrNull()?.contentOrNull ?: "pending"
                    "[$status] $content"
                }
                AcpSessionEvent.PlanUpdate(sessionId, items)
            }
            "usage_update" -> {
                val used = update["used"]?.asPrimitiveOrNull()?.intOrNull
                    ?: update["usedTokens"]?.asPrimitiveOrNull()?.intOrNull
                    ?: 0
                val max = update["size"]?.asPrimitiveOrNull()?.intOrNull
                    ?: update["maxTokens"]?.asPrimitiveOrNull()?.intOrNull
                    ?: 1
                AcpSessionEvent.UsageUpdate(sessionId, used, max)
            }
            else -> null
        }
    }

    fun parsePermissionRequest(rpcId: JsonElement, params: JsonObject): AcpPermissionRequest {
        val sessionId = params["sessionId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
        val toolCall = params["toolCall"]?.asObjectOrNull()
        val toolCallId = toolCall?.get("toolCallId")?.asPrimitiveOrNull()?.contentOrNull ?: ""
        val title = toolCall?.get("title")?.asPrimitiveOrNull()?.contentOrNull ?: "Tool permission requested"
        val kind = toolCall?.get("kind")?.asPrimitiveOrNull()?.contentOrNull ?: "other"
        val rawInput = toolCall?.get("rawInput")?.toString() ?: ""

        val options = params["options"]?.asArrayOrNull()?.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj["optionId"]?.asPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.asPrimitiveOrNull()?.contentOrNull ?: id
            val optKind = obj["kind"]?.asPrimitiveOrNull()?.contentOrNull ?: "allow_once"
            AcpPermissionOption(optionId = id, name = name, kind = optKind)
        } ?: listOf(
            AcpPermissionOption("allow_once", "Allow Once", "allow_once"),
            AcpPermissionOption("allow_always", "Allow Session", "allow_always"),
            AcpPermissionOption("reject_once", "Deny", "reject_once")
        )

        return AcpPermissionRequest(
            rpcId = rpcId,
            sessionId = sessionId,
            toolCallId = toolCallId,
            title = title,
            kind = kind,
            rawInputPreview = rawInput,
            options = options
        )
    }

    private fun extractChunkText(contentElem: JsonElement?): String? {
        if (contentElem == null) return null
        return when (contentElem) {
            is JsonPrimitive -> contentElem.contentOrNull
            is JsonObject -> contentElem["text"]?.asPrimitiveOrNull()?.contentOrNull
            is JsonArray -> contentElem.joinToString("") { item ->
                item.asObjectOrNull()?.get("text")?.asPrimitiveOrNull()?.contentOrNull ?: ""
            }
        }
    }

    private fun parseSubagentEntry(obj: JsonObject): AcpSubagentInfo? {
        val id = obj["subagentId"]?.asPrimitiveOrNull()?.contentOrNull
            ?: obj["sessionId"]?.asPrimitiveOrNull()?.contentOrNull
            ?: obj["id"]?.asPrimitiveOrNull()?.contentOrNull
            ?: return null
        val name = obj["title"]?.asPrimitiveOrNull()?.contentOrNull
            ?: obj["name"]?.asPrimitiveOrNull()?.contentOrNull
            ?: ""
        val role = obj["description"]?.asPrimitiveOrNull()?.contentOrNull
            ?: obj["role"]?.asPrimitiveOrNull()?.contentOrNull
            ?: ""
        val status = obj["state"]?.asObjectOrNull()?.get("state")?.asPrimitiveOrNull()?.contentOrNull
            ?: obj["status"]?.asPrimitiveOrNull()?.contentOrNull
            ?: "active"
        return AcpSubagentInfo(id = id, name = name, role = role, status = status)
    }

    private fun parseSubagentUpdate(sessionId: String, update: JsonObject): AcpSessionEvent.SubagentUpdate? {
        val list = update["subagents"]?.asArrayOrNull()
            ?.mapNotNull { it.asObjectOrNull()?.let(::parseSubagentEntry) }
            ?: listOfNotNull(parseSubagentEntry(update))
        return if (list.isEmpty()) null else AcpSessionEvent.SubagentUpdate(sessionId, list)
    }

    private fun parseToolCall(sessionId: String, update: JsonObject): AcpSessionEvent.ToolCallEvent {
        val toolCallId = update["toolCallId"]?.asPrimitiveOrNull()?.contentOrNull ?: ""
        val title = update["title"]?.asPrimitiveOrNull()?.contentOrNull ?: "Tool"
        val kind = update["kind"]?.asPrimitiveOrNull()?.contentOrNull ?: "other"
        val status = update["status"]?.asPrimitiveOrNull()?.contentOrNull ?: "in_progress"

        val textParts = mutableListOf<String>()
        var diffPath: String? = null
        var diffOld: String? = null
        var diffNew: String? = null
        var imageBase64: String? = null

        val contentArr = update["content"]?.asArrayOrNull()
        contentArr?.forEach { blockElem ->
            val block = blockElem.asObjectOrNull() ?: return@forEach
            when (block["type"]?.asPrimitiveOrNull()?.contentOrNull) {
                "content" -> {
                    val inner = block["content"]?.asObjectOrNull()
                    val innerType = inner?.get("type")?.asPrimitiveOrNull()?.contentOrNull
                    if (innerType == "text") {
                        inner["text"]?.asPrimitiveOrNull()?.contentOrNull?.let(textParts::add)
                    } else if (innerType == "image") {
                        imageBase64 = inner["data"]?.asPrimitiveOrNull()?.contentOrNull
                    }
                }
                "text" -> {
                    block["text"]?.asPrimitiveOrNull()?.contentOrNull?.let(textParts::add)
                }
                "image" -> {
                    imageBase64 = block["data"]?.asPrimitiveOrNull()?.contentOrNull
                }
                "diff" -> {
                    diffPath = block["path"]?.asPrimitiveOrNull()?.contentOrNull
                    diffOld = block["oldText"]?.asPrimitiveOrNull()?.contentOrNull
                    diffNew = block["newText"]?.asPrimitiveOrNull()?.contentOrNull
                }
            }
        }

        if (textParts.isEmpty()) {
            update["rawInput"]?.let { input ->
                if (input.toString() != "{}" && input.toString() != "null") {
                    textParts.add(input.toString())
                }
            }
        }

        return AcpSessionEvent.ToolCallEvent(
            sessionId = sessionId,
            toolCallId = toolCallId,
            title = title,
            kind = kind,
            status = status,
            textOutput = textParts.joinToString("\n"),
            diffPath = diffPath,
            diffOld = diffOld,
            diffNew = diffNew,
            imageBase64 = imageBase64
        )
    }

    fun buildPermissionResponse(rpcId: JsonElement, optionId: String?): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", rpcId)
            put("result", buildJsonObject {
                put("outcome", buildJsonObject {
                    if (optionId != null) {
                        put("outcome", "selected")
                        put("optionId", optionId)
                    } else {
                        put("outcome", "cancelled")
                    }
                })
            })
        }
}
