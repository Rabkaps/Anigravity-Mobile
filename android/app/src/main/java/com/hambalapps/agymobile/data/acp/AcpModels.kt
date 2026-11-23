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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    data class UserMessageChunk(val sessionId: String, val text: String) : AcpSessionEvent
    data class AgentMessageChunk(val sessionId: String, val text: String) : AcpSessionEvent
    data class AgentThoughtChunk(val sessionId: String, val text: String) : AcpSessionEvent
    data class SubagentUpdate(val sessionId: String, val subagents: List<AcpSubagentInfo>) : AcpSessionEvent
    data class ToolCallEvent(
        val sessionId: String,
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
    data class PlanUpdate(val sessionId: String, val entries: List<String>) : AcpSessionEvent
    data class ModeUpdate(val sessionId: String, val modeId: String) : AcpSessionEvent
    data class ConfigOptionsUpdate(
        val sessionId: String,
        val configOptions: List<AcpConfigOption>
    ) : AcpSessionEvent
    data class AvailableCommandsUpdate(
        val sessionId: String,
        val commands: List<AvailableCommand>
    ) : AcpSessionEvent
    data class UsageUpdate(
        val sessionId: String,
        val usedTokens: Int,
        val maxTokens: Int
    ) : AcpSessionEvent
}

object AcpParser {

    fun parseInitializeResult(result: JsonElement): AcpInitializeResult {
        val obj = runCatching { result.jsonObject }.getOrNull() ?: return AcpInitializeResult()
        val protocolVersion = obj["protocolVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val agentInfo = obj["agentInfo"]?.jsonObject
        val agentName = agentInfo?.get("title")?.jsonPrimitive?.contentOrNull
            ?: agentInfo?.get("name")?.jsonPrimitive?.contentOrNull
            ?: "Antigravity ACP Server"
        val agentVersion = agentInfo?.get("version")?.jsonPrimitive?.contentOrNull ?: ""

        val caps = obj["agentCapabilities"]?.jsonObject
        val loadSession = caps?.get("loadSession")?.jsonPrimitive?.booleanOrNull ?: false
        val sessionCaps = caps?.get("sessionCapabilities")?.jsonObject
        val supportsList = sessionCaps?.get("list") != null
        val supportsResume = sessionCaps?.get("resume") != null
        val supportsLogout = caps?.get("auth")?.jsonObject?.get("logout") != null

        val authMethods = runCatching { obj["authMethods"]?.jsonArray }.getOrNull()
            ?.mapNotNull { elem ->
                val m = elem.jsonObject
                val id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = m["name"]?.jsonPrimitive?.contentOrNull ?: id
                val desc = m["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val type = m["type"]?.jsonPrimitive?.contentOrNull ?: "agent"
                val args = runCatching {
                    m["args"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                }.getOrNull().orEmpty()
                val env = runCatching {
                    m["env"]?.jsonObject?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
                }.getOrNull().orEmpty()
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
        val obj = runCatching { result.jsonObject }.getOrNull()
            ?: return AcpSessionSetupResult("", emptyList())
        val sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull ?: ""

        val parsedOptions = parseConfigOptionsArray(obj["configOptions"]).toMutableList()

        // Backwards compatibility: synthesize mode/model options if server omitted configOptions
        val hasMode = parsedOptions.any { it.category == "mode" || it.id == "mode" }
        if (!hasMode) {
            obj["modes"]?.jsonObject?.let { modesObj ->
                val current = modesObj["currentModeId"]?.jsonPrimitive?.contentOrNull ?: ""
                val available = runCatching { modesObj["availableModes"]?.jsonArray }.getOrNull()
                    ?.mapNotNull { elem ->
                        val m = elem.jsonObject
                        val id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val name = m["name"]?.jsonPrimitive?.contentOrNull ?: id
                        val desc = m["description"]?.jsonPrimitive?.contentOrNull ?: ""
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
            obj["models"]?.jsonObject?.let { modelsObj ->
                val current = modelsObj["currentModelId"]?.jsonPrimitive?.contentOrNull ?: ""
                val available = runCatching { modelsObj["availableModels"]?.jsonArray }.getOrNull()
                    ?.mapNotNull { elem ->
                        val m = elem.jsonObject
                        val id = m["modelId"]?.jsonPrimitive?.contentOrNull
                            ?: m["id"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                        val name = m["name"]?.jsonPrimitive?.contentOrNull ?: id
                        val desc = m["description"]?.jsonPrimitive?.contentOrNull ?: ""
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
        val arr = runCatching { elem?.jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
            val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val category = obj["category"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "select"
            val currentValue = obj["currentValue"]?.jsonPrimitive?.contentOrNull ?: ""
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
        val arr = runCatching { elem?.jsonArray }.getOrNull() ?: return emptyList()
        val choices = mutableListOf<AcpConfigChoice>()
        for (entry in arr) {
            val obj = runCatching { entry.jsonObject }.getOrNull() ?: continue
            val groupOptions = runCatching { obj["options"]?.jsonArray }.getOrNull()
            if (groupOptions != null) {
                val groupName = obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["group"]?.jsonPrimitive?.contentOrNull
                for (sub in groupOptions) {
                    val subObj = runCatching { sub.jsonObject }.getOrNull() ?: continue
                    val value = subObj["value"]?.jsonPrimitive?.contentOrNull ?: continue
                    val name = subObj["name"]?.jsonPrimitive?.contentOrNull ?: value
                    val desc = subObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    choices.add(AcpConfigChoice(value = value, name = name, description = desc, group = groupName))
                }
            } else {
                val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: value
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                choices.add(AcpConfigChoice(value = value, name = name, description = desc))
            }
        }
        return choices
    }

    fun parseSessionList(result: JsonElement): List<SessionSummary> {
        val obj = runCatching { result.jsonObject }.getOrNull() ?: return emptyList()
        val arr = runCatching { obj["sessions"]?.jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { elem ->
            val s = elem.jsonObject
            val id = s["sessionId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = s["title"]?.jsonPrimitive?.contentOrNull?.ifBlank { id } ?: id
            val cwd = s["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
            val updatedAt = s["updatedAt"]?.jsonPrimitive?.contentOrNull ?: ""
            SessionSummary(id = id, title = title, cwd = cwd, updatedAt = updatedAt)
        }
    }

    fun parseElicitationRequest(rpcId: JsonElement, params: JsonObject): AcpElicitationRequest {
        val sessionId = params["sessionId"]?.jsonPrimitive?.contentOrNull ?: ""
        val mode = params["mode"]?.jsonPrimitive?.contentOrNull ?: "form"
        val message = params["message"]?.jsonPrimitive?.contentOrNull ?: "Agent requested input"
        val url = params["url"]?.jsonPrimitive?.contentOrNull

        val schemaObj = runCatching { params["requestedSchema"]?.jsonObject }.getOrNull()
        val requiredKeys = runCatching {
            schemaObj?.get("required")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
        }.getOrNull().orEmpty()
        val properties = runCatching { schemaObj?.get("properties")?.jsonObject }.getOrNull()

        val fields = properties?.entries?.map { (key, rawDef) ->
            val def = rawDef.jsonObject
            val title = def["title"]?.jsonPrimitive?.contentOrNull ?: key
            val desc = def["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = def["type"]?.jsonPrimitive?.contentOrNull ?: "string"
            val enumOpts = runCatching {
                def["enum"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            }.getOrNull().orEmpty()
            val defVal = def["default"]?.jsonPrimitive?.contentOrNull ?: ""
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
        val sessionId = params["sessionId"]?.jsonPrimitive?.contentOrNull ?: ""
        val update = params["update"]?.jsonObject ?: return null
        val kind = update["sessionUpdate"]?.jsonPrimitive?.contentOrNull ?: return null

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
                val modeId = update["currentModeId"]?.jsonPrimitive?.contentOrNull ?: return null
                AcpSessionEvent.ModeUpdate(sessionId, modeId)
            }
            "config_option_update" -> {
                val opts = parseConfigOptionsArray(update["configOptions"])
                AcpSessionEvent.ConfigOptionsUpdate(sessionId, opts)
            }
            "available_commands_update" -> {
                val arr = update["availableCommands"]?.jsonArray ?: return null
                val cmds = arr.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    AvailableCommand(name = name, description = desc)
                }
                AcpSessionEvent.AvailableCommandsUpdate(sessionId, cmds)
            }
            "plan" -> {
                val arr = update["entries"]?.jsonArray ?: return null
                val items = arr.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                    "[$status] $content"
                }
                AcpSessionEvent.PlanUpdate(sessionId, items)
            }
            "usage_update" -> {
                val used = update["used"]?.jsonPrimitive?.intOrNull
                    ?: update["usedTokens"]?.jsonPrimitive?.intOrNull
                    ?: 0
                val max = update["size"]?.jsonPrimitive?.intOrNull
                    ?: update["maxTokens"]?.jsonPrimitive?.intOrNull
                    ?: 1
                AcpSessionEvent.UsageUpdate(sessionId, used, max)
            }
            else -> null
        }
    }

    fun parsePermissionRequest(rpcId: JsonElement, params: JsonObject): AcpPermissionRequest {
        val sessionId = params["sessionId"]?.jsonPrimitive?.contentOrNull ?: ""
        val toolCall = params["toolCall"]?.jsonObject
        val toolCallId = toolCall?.get("toolCallId")?.jsonPrimitive?.contentOrNull ?: ""
        val title = toolCall?.get("title")?.jsonPrimitive?.contentOrNull ?: "Tool permission requested"
        val kind = toolCall?.get("kind")?.jsonPrimitive?.contentOrNull ?: "other"
        val rawInput = toolCall?.get("rawInput")?.toString() ?: ""

        val options = params["options"]?.jsonArray?.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["optionId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
            val optKind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "allow_once"
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
            is JsonObject -> contentElem["text"]?.jsonPrimitive?.contentOrNull
            else -> contentElem.jsonArray.joinToString("") { item ->
                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            }
        }
    }

    private fun parseSubagentEntry(obj: JsonObject): AcpSubagentInfo? {
        val id = obj["subagentId"]?.jsonPrimitive?.contentOrNull
            ?: obj["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val name = obj["title"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val role = obj["description"]?.jsonPrimitive?.contentOrNull
            ?: obj["role"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val status = runCatching {
            obj["state"]?.jsonObject?.get("state")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
            ?: obj["status"]?.jsonPrimitive?.contentOrNull
            ?: "active"
        return AcpSubagentInfo(id = id, name = name, role = role, status = status)
    }

    private fun parseSubagentUpdate(sessionId: String, update: JsonObject): AcpSessionEvent.SubagentUpdate? {
        val list = runCatching { update["subagents"]?.jsonArray }.getOrNull()
            ?.mapNotNull { runCatching { parseSubagentEntry(it.jsonObject) }.getOrNull() }
            ?: listOfNotNull(parseSubagentEntry(update))
        return if (list.isEmpty()) null else AcpSessionEvent.SubagentUpdate(sessionId, list)
    }

    private fun parseToolCall(sessionId: String, update: JsonObject): AcpSessionEvent.ToolCallEvent {
        val toolCallId = update["toolCallId"]?.jsonPrimitive?.contentOrNull ?: ""
        val title = update["title"]?.jsonPrimitive?.contentOrNull ?: "Tool"
        val kind = update["kind"]?.jsonPrimitive?.contentOrNull ?: "other"
        val status = update["status"]?.jsonPrimitive?.contentOrNull ?: "in_progress"

        val textParts = mutableListOf<String>()
        var diffPath: String? = null
        var diffOld: String? = null
        var diffNew: String? = null
        var imageBase64: String? = null

        val contentArr = runCatching { update["content"]?.jsonArray }.getOrNull()
        contentArr?.forEach { blockElem ->
            val block = blockElem.jsonObject
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "content" -> {
                    val inner = block["content"]?.jsonObject
                    val innerType = inner?.get("type")?.jsonPrimitive?.contentOrNull
                    if (innerType == "text") {
                        inner["text"]?.jsonPrimitive?.contentOrNull?.let(textParts::add)
                    } else if (innerType == "image") {
                        imageBase64 = inner["data"]?.jsonPrimitive?.contentOrNull
                    }
                }
                "text" -> {
                    block["text"]?.jsonPrimitive?.contentOrNull?.let(textParts::add)
                }
                "image" -> {
                    imageBase64 = block["data"]?.jsonPrimitive?.contentOrNull
                }
                "diff" -> {
                    diffPath = block["path"]?.jsonPrimitive?.contentOrNull
                    diffOld = block["oldText"]?.jsonPrimitive?.contentOrNull
                    diffNew = block["newText"]?.jsonPrimitive?.contentOrNull
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
