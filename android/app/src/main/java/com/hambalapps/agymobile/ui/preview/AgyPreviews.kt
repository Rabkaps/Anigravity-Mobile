package com.hambalapps.agymobile.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpConfigChoice
import com.hambalapps.agymobile.data.acp.AcpConfigOption
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.data.local.AppPreferences
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.ui.chat.ChatMessageUi
import com.hambalapps.agymobile.ui.chat.ChatUiState
import com.hambalapps.agymobile.ui.chat.EmptyChatHero
import com.hambalapps.agymobile.ui.chat.ExpressiveTopBar
import com.hambalapps.agymobile.ui.chat.FloatingComposerSection
import com.hambalapps.agymobile.ui.chat.MessageBubbleItem
import com.hambalapps.agymobile.ui.chat.MessageRole
import com.hambalapps.agymobile.ui.chat.ToolCallUi
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.components.ContextRingIndicator
import com.hambalapps.agymobile.ui.components.MorphingActionButton
import com.hambalapps.agymobile.ui.components.PrivilegeBadge
import com.hambalapps.agymobile.ui.theme.AgyExpressiveTheme
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape

private fun samplePreferences(): AppPreferences {
    val profile = ConnectionProfile(
        id = "local-termux",
        name = "On-Device Termux",
        host = "127.0.0.1",
        port = 4789,
        token = "demo-token",
        useTls = false,
        isRemote = false
    )
    return AppPreferences(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        amoledDark = true,
        dynamicColor = false,
        selectedModel = "gemini-3-pro",
        selectedEffort = "high",
        selectedMode = "accept-edits"
    )
}

@Preview(
    name = "Home & 1-Tap Setup (AMOLED)",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ChatEmptyHeroPreview() {
    val state = ChatUiState(
        preferences = samplePreferences(),
        connectionState = BridgeConnectionState.DISCONNECTED,
        usedTokens = 0,
        maxTokens = 1_000_000
    )

    AgyExpressiveTheme(darkTheme = true, dynamicColor = false, amoledDark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ExpressiveTopBar(
                    state = state,
                    showMenuButton = true,
                    onOpenDrawer = {},
                    onOpenModeSheet = {},
                    onOpenMcpSheet = {}
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EmptyChatHero(
                        state = state,
                        onQuickPrompt = {},
                        onAutoSetupBridge = {},
                        onOpenTerminal = {},
                        onOpenSettings = {}
                    )
                }
                FloatingComposerSection(
                    state = state,
                    onTextChange = {},
                    onSend = {},
                    onStop = {},
                    onSlashTap = {},
                    onVoiceTap = {},
                    onOpenModeSheet = {},
                    onOpenDiffSheet = {},
                    onOpenArtifactSheet = {},
                    onOpenTerminal = {}
                )
            }
        }
    }
}

@Preview(
    name = "Active Chat & Streaming",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ChatConversationPreview() {
    val state = ChatUiState(
        preferences = samplePreferences(),
        connectionState = BridgeConnectionState.CONNECTED,
        health = HealthResponse(
            ok = true,
            bridgeVersion = "1.0.0",
            agyVersion = "1.4.8",
            platform = "android",
            arch = "arm64",
            isTermux = true,
            privilegeLevel = "root"
        ),
        configOptions = listOf(
            AcpConfigOption(
                id = "mode",
                name = "Mode",
                category = "mode",
                currentValue = "accept-edits",
                options = listOf(AcpConfigChoice("accept-edits", "Accept Edits"))
            ),
            AcpConfigOption(
                id = "thought_level",
                name = "Thinking",
                category = "thought_level",
                currentValue = "high",
                options = listOf(AcpConfigChoice("high", "High"))
            ),
            AcpConfigOption(
                id = "model",
                name = "Model",
                category = "model",
                currentValue = "gemini-2.5-pro",
                options = listOf(AcpConfigChoice("gemini-2.5-pro", "Gemini 2.5 Pro"))
            )
        ),
        usedTokens = 420_000,
        maxTokens = 1_000_000,
        isStreaming = true,
        messages = listOf(
            ChatMessageUi(
                id = "1",
                role = MessageRole.USER,
                text = "Check what app is open on my phone and toggle dark theme via su."
            ),
            ChatMessageUi(
                id = "2",
                role = MessageRole.AGENT,
                thinking = "Querying android_ui_tree via android-control-mcp (root backend) to locate display settings.",
                toolCalls = listOf(
                    ToolCallUi(
                        toolCallId = "tc-1",
                        title = "android_ui_tree",
                        kind = "other",
                        status = "completed",
                        textOutput = "{\"nodeCount\": 14, \"foreground\": \"com.android.settings\"}"
                    )
                ),
                text = "Found **Settings** open in the foreground (`com.android.settings`).\n\n```kotlin\n// Tapping Dark theme switch via android_tap\nandroid_tap(x = 890, y = 640)\n```",
                isStreaming = true
            )
        )
    )

    AgyExpressiveTheme(darkTheme = true, dynamicColor = false, amoledDark = true) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ExpressiveTopBar(
                    state = state,
                    showMenuButton = true,
                    onOpenDrawer = {},
                    onOpenModeSheet = {},
                    onOpenMcpSheet = {}
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubbleItem(
                            message = msg,
                            onOpenDiff = {}
                        )
                    }
                }
                FloatingComposerSection(
                    state = state,
                    onTextChange = {},
                    onSend = {},
                    onStop = {},
                    onSlashTap = {},
                    onVoiceTap = {},
                    onOpenModeSheet = {},
                    onOpenDiffSheet = {},
                    onOpenArtifactSheet = {},
                    onOpenTerminal = {}
                )
            }
        }
    }
}

@Preview(
    name = "M3 Expressive Controls",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ExpressiveControlsPreview() {
    AgyExpressiveTheme(darkTheme = true, dynamicColor = false, amoledDark = true) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = M3ExpressiveShape.ExtraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Execution Mode (--mode)", style = MaterialTheme.typography.titleMedium)
                ConnectedButtonGroup(
                    options = listOf("Default", "Edits", "Plan", "Auto"),
                    selectedIndex = 1,
                    onSelect = {}
                )

                Text("Reasoning Effort (/effort)", style = MaterialTheme.typography.titleMedium)
                ConnectedButtonGroup(
                    options = listOf("Low", "Med", "High", "Max"),
                    selectedIndex = 2,
                    onSelect = {}
                )

                Text("Status & Morphing Controls", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrivilegeBadge(
                        privilegeLevel = "root",
                        connectionState = BridgeConnectionState.CONNECTED,
                        isRemote = false,
                        onClick = {}
                    )
                    PrivilegeBadge(
                        privilegeLevel = "shizuku",
                        connectionState = BridgeConnectionState.CONNECTED,
                        isRemote = false,
                        onClick = {}
                    )
                    PrivilegeBadge(
                        privilegeLevel = "none",
                        connectionState = BridgeConnectionState.CONNECTED,
                        isRemote = true,
                        onClick = {}
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContextRingIndicator(usageRatio = 0.42f, onClick = {})
                    MorphingActionButton(
                        isStreaming = false,
                        enabled = true,
                        onSend = {},
                        onStop = {}
                    )
                    MorphingActionButton(
                        isStreaming = true,
                        enabled = true,
                        onSend = {},
                        onStop = {}
                    )
                }
            }
        }
    }
}
