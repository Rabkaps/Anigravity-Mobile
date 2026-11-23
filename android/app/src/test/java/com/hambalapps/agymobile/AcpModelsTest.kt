package com.hambalapps.agymobile

import com.hambalapps.agymobile.data.acp.AcpParser
import com.hambalapps.agymobile.data.acp.AcpSessionEvent
import com.hambalapps.agymobile.data.acp.QuotaSummaryResponse
import com.hambalapps.agymobile.ui.chat.ChatUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseInitializeResult_readsCapabilitiesAndAuthMethods() {
        val raw = """
            {
              "protocolVersion": 1,
              "agentInfo": { "name": "antigravity", "title": "Antigravity ACP Server", "version": "1.5.14" },
              "agentCapabilities": {
                "loadSession": true,
                "sessionCapabilities": { "list": {}, "resume": {} },
                "auth": { "logout": {} }
              },
              "authMethods": [
                { "id": "oauth-personal", "name": "Log in with Google", "description": "Personal account" },
                { "id": "terminal-login", "name": "Log in from terminal", "type": "terminal", "args": ["acp", "--login"] }
              ]
            }
        """.trimIndent()

        val res = AcpParser.parseInitializeResult(json.parseToJsonElement(raw))
        assertEquals("Antigravity ACP Server", res.agentName)
        assertTrue(res.supportsLoadSession)
        assertTrue(res.supportsSessionList)
        assertTrue(res.supportsLogout)
        assertEquals(2, res.authMethods.size)
        assertEquals("terminal", res.authMethods[1].type)
        assertEquals(listOf("acp", "--login"), res.authMethods[1].args)
    }

    @Test
    fun parseSessionSetupResult_readsDynamicConfigOptions() {
        val raw = """
            {
              "sessionId": "sess-xyz",
              "configOptions": [
                {
                  "id": "model",
                  "name": "Model",
                  "category": "model",
                  "type": "select",
                  "currentValue": "g-flash",
                  "options": [
                    { "value": "g-flash", "name": "Gemini Flash", "description": "Fast" },
                    { "value": "g-pro", "name": "Gemini Pro", "description": "Deep" }
                  ]
                },
                {
                  "id": "thought_level",
                  "name": "Thinking Level",
                  "category": "thought_level",
                  "type": "select",
                  "currentValue": "high",
                  "options": [
                    { "value": "low", "name": "Low" },
                    { "value": "medium", "name": "Medium" },
                    { "value": "high", "name": "High" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val setup = AcpParser.parseSessionSetupResult(json.parseToJsonElement(raw))
        assertEquals("sess-xyz", setup.sessionId)
        assertEquals(2, setup.configOptions.size)
        assertEquals("Gemini Flash", setup.configOptions[0].currentDisplayLabel())
        assertEquals(3, setup.configOptions[1].options.size)
    }

    @Test
    fun parseSessionList_parsesRealAcpSessions() {
        val raw = """
            {
              "sessions": [
                { "sessionId": "s1", "cwd": "/tmp/app", "title": "Fix auth", "updatedAt": "2026-11-22T12:00:00Z" }
              ]
            }
        """.trimIndent()
        val list = AcpParser.parseSessionList(json.parseToJsonElement(raw))
        assertEquals(1, list.size)
        assertEquals("s1", list[0].id)
        assertEquals("Fix auth", list[0].title)
    }

    @Test
    fun parseSessionUpdate_extractsAgentMessageChunk() {
        val raw = """
            {
              "sessionId": "sess-1",
              "update": {
                "sessionUpdate": "agent_message_chunk",
                "content": { "type": "text", "text": "Hello from agy" }
              }
            }
        """.trimIndent()
        val obj = json.parseToJsonElement(raw).jsonObject
        val event = AcpParser.parseSessionUpdate(obj)

        assertTrue(event is AcpSessionEvent.AgentMessageChunk)
        val chunk = event as AcpSessionEvent.AgentMessageChunk
        assertEquals("sess-1", chunk.sessionId)
        assertEquals("Hello from agy", chunk.text)
    }

    @Test
    fun parseSessionUpdate_extractsUserMessageChunkAndSubagentUpdate() {
        val userRaw = """
            {
              "sessionId": "sess-1",
              "update": {
                "sessionUpdate": "user_message_chunk",
                "content": { "type": "text", "text": "Replay prompt" }
              }
            }
        """.trimIndent()
        val userEvent = AcpParser.parseSessionUpdate(json.parseToJsonElement(userRaw).jsonObject)
        assertTrue(userEvent is AcpSessionEvent.UserMessageChunk)
        assertEquals("Replay prompt", (userEvent as AcpSessionEvent.UserMessageChunk).text)

        val subRaw = """
            {
              "sessionId": "sess-1",
              "update": {
                "sessionUpdate": "subagent_update",
                "sessionId": "sess_child_1",
                "title": "Test investigator",
                "description": "Investigates test failures",
                "state": { "state": "running" }
              }
            }
        """.trimIndent()
        val subEvent = AcpParser.parseSessionUpdate(json.parseToJsonElement(subRaw).jsonObject)
        assertTrue(subEvent is AcpSessionEvent.SubagentUpdate)
        val sub = (subEvent as AcpSessionEvent.SubagentUpdate).subagents.single()
        assertEquals("sess_child_1", sub.id)
        assertEquals("Test investigator", sub.name)
        assertEquals("running", sub.status)
    }

    @Test
    fun parseSessionUpdate_extractsScreenshotImageInToolCall() {
        val raw = """
            {
              "sessionId": "sess-1",
              "update": {
                "sessionUpdate": "tool_call_update",
                "toolCallId": "tc-99",
                "title": "android_screenshot",
                "kind": "other",
                "status": "completed",
                "content": [
                  {
                    "type": "content",
                    "content": { "type": "image", "data": "iVBORw0KGgo=", "mimeType": "image/png" }
                  }
                ]
              }
            }
        """.trimIndent()
        val obj = json.parseToJsonElement(raw).jsonObject
        val event = AcpParser.parseSessionUpdate(obj) as AcpSessionEvent.ToolCallEvent

        assertEquals("tc-99", event.toolCallId)
        assertEquals("completed", event.status)
        assertEquals("iVBORw0KGgo=", event.imageBase64)
    }

    @Test
    fun parsePermissionRequest_andBuildSelectedReply() {
        val raw = """
            {
              "sessionId": "sess-1",
              "toolCall": {
                "toolCallId": "tc-1",
                "title": "android_tap (500, 900)",
                "kind": "execute",
                "rawInput": { "x": 500, "y": 900 }
              },
              "options": [
                { "optionId": "allow_once", "name": "Allow Once", "kind": "allow_once" },
                { "optionId": "reject_once", "name": "Deny", "kind": "reject_once" }
              ]
            }
        """.trimIndent()
        val req = AcpParser.parsePermissionRequest(
            rpcId = JsonPrimitive(42),
            params = json.parseToJsonElement(raw).jsonObject
        )

        assertEquals("android_tap (500, 900)", req.title)
        assertEquals(2, req.options.size)

        val reply = AcpParser.buildPermissionResponse(req.rpcId, "allow_once")
        val outcome = reply["result"]!!.jsonObject["outcome"]!!.jsonObject
        assertEquals("selected", outcome["outcome"]!!.jsonPrimitive.content)
        assertEquals("allow_once", outcome["optionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun quotaSummaryResponse_deserializesAndMatchesModelFamilies() {
        val raw = """
            {
              "ok": true,
              "email": "dev@example.com",
              "buckets": [
                {
                  "bucketId": "gemini-pro",
                  "displayName": "Gemini Pro",
                  "window": "5h",
                  "remainingFraction": 0.64,
                  "resetTime": "2026-11-23T14:00:00Z"
                }
              ],
              "models": {
                "gemini-4-argon-medium": {
                  "modelId": "gemini-4-argon-medium",
                  "displayName": "Gemini 4 Argon (Medium)",
                  "remainingFraction": 0.64,
                  "quotaBucketName": "Gemini Pro"
                },
                "gemini-4-argon": {
                  "modelId": "gemini-4-argon-medium",
                  "displayName": "Gemini 4 Argon (Medium)",
                  "remainingFraction": 0.64,
                  "quotaBucketName": "Gemini Pro"
                },
                "__gemini_pool__": {
                  "modelId": "gemini-3.8-flash-tiered",
                  "remainingFraction": 0.82
                }
              }
            }
        """.trimIndent()

        val summary = json.decodeFromString<QuotaSummaryResponse>(raw)
        val state = ChatUiState(quotaSummary = summary)

        val exact = state.quotaForModelChoice("gemini-4-argon-medium", "Gemini 4 Argon (Medium)")
        assertEquals(0.64, exact?.remainingFraction ?: -1.0, 0.001)

        val byFamily = state.quotaForModelChoice("gemini-4-argon-high", "Gemini 4 Argon (High)")
        assertEquals(0.64, byFamily?.remainingFraction ?: -1.0, 0.001)

        val geminiPoolFallback = state.quotaForModelChoice("gemini-4-neon", "Gemini 4 Neon")
        assertEquals(0.82, geminiPoolFallback?.remainingFraction ?: -1.0, 0.001)
    }
}
