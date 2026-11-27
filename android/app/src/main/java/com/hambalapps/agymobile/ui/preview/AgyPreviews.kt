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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpConfigChoice
import com.hambalapps.agymobile.data.acp.AcpConfigOption
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.data.acp.ModelQuotaInfo
import com.hambalapps.agymobile.data.acp.SessionSummary
import com.hambalapps.agymobile.data.local.AppPreferences
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.data.local.LocalProject
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
import com.hambalapps.agymobile.ui.sheets.ModeAndModelSheetContent
import com.hambalapps.agymobile.ui.sheets.SessionsDrawerContent
import com.hambalapps.agymobile.ui.theme.AgyExpressiveTheme
import com.hambalapps.agymobile.ui.theme.AppThemeMode
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.SolarPhase

enum class PreviewThemeMode(val darkTheme: Boolean, val amoledDark: Boolean) {
    LIGHT(darkTheme = false, amoledDark = false),
    DARK(darkTheme = true, amoledDark = false),
    AMOLED(darkTheme = true, amoledDark = true)
}

class ThemeModeProvider : PreviewParameterProvider<PreviewThemeMode> {
    override val values: Sequence<PreviewThemeMode> = PreviewThemeMode.entries.asSequence()
}

class SolarPhaseProvider : PreviewParameterProvider<SolarPhase> {
    override val values: Sequence<SolarPhase> = SolarPhase.entries.asSequence()
}

private fun samplePreferences(amoledDark: Boolean = false): AppPreferences {
    val profile = ConnectionProfile(
        id = "local-standalone",
        name = "Local",
        host = "127.0.0.1",
        port = 4789,
        token = "demo-token",
        useTls = false,
        isRemote = false,
        isStandalone = true
    )
    return AppPreferences(
        profiles = listOf(profile),
        activeProfileId = profile.id,
        amoledDark = amoledDark,
        dynamicColor = false,
        selectedModel = "gemini-2.5-pro",
        selectedEffort = "high",
        selectedMode = "accept-edits"
    )
}

@Preview(
    name = "Home",
    showSystemUi = true
)
@Composable
fun ChatEmptyHeroPreview(
    @PreviewParameter(ThemeModeProvider::class) themeMode: PreviewThemeMode
) {
    val state = ChatUiState(
        preferences = samplePreferences(themeMode.amoledDark),
        connectionState = BridgeConnectionState.CONNECTED,
        standaloneInstalled = true,
        standaloneSignedIn = true,
        configOptions = listOf(
            AcpConfigOption(
                id = "model",
                name = "Model",
                category = "model",
                currentValue = "gemini-2.5-pro",
                options = listOf(AcpConfigChoice("gemini-2.5-pro", "Gemini 2.5 Pro"))
            )
        ),
        usedTokens = 0,
        maxTokens = 1_000_000
    )

    AgyExpressiveTheme(
        darkTheme = themeMode.darkTheme,
        dynamicColor = false,
        amoledDark = themeMode.amoledDark
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ExpressiveTopBar(
                    state = state,
                    showMenuButton = true,
                    onOpenDrawer = {},
                    onOpenModeSheet = {},
                    onNewChat = {}
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
    name = "Solar Dynamic Theme",
    showSystemUi = true
)
@Composable
fun SolarDynamicThemePreview(
    @PreviewParameter(SolarPhaseProvider::class) phase: SolarPhase
) {
    val state = ChatUiState(
        preferences = samplePreferences(amoledDark = false),
        connectionState = BridgeConnectionState.CONNECTED,
        standaloneInstalled = true,
        standaloneSignedIn = true,
        configOptions = listOf(
            AcpConfigOption(
                id = "model",
                name = "Model",
                category = "model",
                currentValue = "gemini-2.5-pro",
                options = listOf(AcpConfigChoice("gemini-2.5-pro", "Gemini 2.5 Pro"))
            )
        ),
        usedTokens = 0,
        maxTokens = 1_000_000
    )

    AgyExpressiveTheme(
        themeMode = AppThemeMode.SOLAR,
        solarPhaseOverride = phase,
        dynamicColor = false
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ExpressiveTopBar(
                    state = state,
                    showMenuButton = true,
                    onOpenDrawer = {},
                    onOpenModeSheet = {},
                    onNewChat = {}
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
    showSystemUi = true
)
@Composable
fun ChatConversationPreview(
    @PreviewParameter(ThemeModeProvider::class) themeMode: PreviewThemeMode
) {
    val state = ChatUiState(
        preferences = samplePreferences(themeMode.amoledDark),
        connectionState = BridgeConnectionState.CONNECTED,
        health = HealthResponse(
            ok = true,
            bridgeVersion = "1.0.0",
            agyVersion = "1.4.8",
            platform = "android",
            arch = "arm64",
            isTermux = false,
            privilegeLevel = "shizuku"
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
                id = "model",
                name = "Model",
                category = "model",
                currentValue = "gemini-2.5-pro",
                options = listOf(AcpConfigChoice("gemini-2.5-pro", "Gemini 2.5 Pro"))
            )
        ),
        usedTokens = 240_000,
        maxTokens = 1_000_000,
        isStreaming = true,
        messages = listOf(
            ChatMessageUi(
                id = "1",
                role = MessageRole.USER,
                text = "Check why the build failed and fix the layout padding."
            ),
            ChatMessageUi(
                id = "2",
                role = MessageRole.AGENT,
                thinking = "Checking Scaffold innerPadding and horizontal spacing in ChatScreen.kt.",
                toolCalls = listOf(
                    ToolCallUi(
                        toolCallId = "tc-1",
                        title = "Edited ChatScreen.kt",
                        kind = "edit",
                        status = "completed",
                        diffPath = "ui/chat/ChatScreen.kt",
                        textOutput = "Updated Scaffold padding and composer insets."
                    )
                ),
                text = "Updated `ChatScreen.kt` to consume window insets cleanly with `16.dp` horizontal padding.\n\n```kotlin\nModifier\n    .fillMaxSize()\n    .navigationBarsPadding()\n    .padding(horizontal = 16.dp)\n```",
                isStreaming = true
            )
        )
    )

    AgyExpressiveTheme(
        darkTheme = themeMode.darkTheme,
        dynamicColor = false,
        amoledDark = themeMode.amoledDark
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ExpressiveTopBar(
                    state = state,
                    showMenuButton = true,
                    onOpenDrawer = {},
                    onOpenModeSheet = {},
                    onNewChat = {}
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
    name = "Navigation Drawer",
    showBackground = true
)
@Composable
fun SessionsDrawerPreview(
    @PreviewParameter(ThemeModeProvider::class) themeMode: PreviewThemeMode
) {
    val prefs = samplePreferences(themeMode.amoledDark)
    AgyExpressiveTheme(
        darkTheme = themeMode.darkTheme,
        dynamicColor = false,
        amoledDark = themeMode.amoledDark
    ) {
        SessionsDrawerContent(
            workspaceCwd = "/storage/emulated/0/Anigravity-Mobile",
            profiles = prefs.profiles,
            activeProfileId = prefs.activeProfileId,
            sessions = listOf(
                SessionSummary(id = "1", title = "Fix layout padding in ChatScreen", updatedAt = "2026-11-27"),
                SessionSummary(id = "2", title = "Optimize Compose recomposition", updatedAt = "2026-11-26"),
                SessionSummary(id = "3", title = "Bridge auth token refresh", updatedAt = "2026-11-25")
            ),
            onNewChat = {},
            onForkChat = {},
            onSelectProfile = {},
            onChangeWorkspace = {},
            onLoadSession = {},
            onOpenSheet = {},
            onOpenTerminal = {},
            onCloseDrawer = {},
            isSignedIn = true,
            projects = listOf(
                LocalProject(
                    id = "p1",
                    name = "Anigravity-Mobile",
                    rootDir = "/storage/emulated/0/Anigravity-Mobile"
                )
            ),
            activeProjectId = "p1"
        )
    }
}

@Preview(
    name = "Model & Mode Sheet",
    showBackground = true
)
@Composable
fun ModeAndModelSheetPreview(
    @PreviewParameter(ThemeModeProvider::class) themeMode: PreviewThemeMode
) {
    AgyExpressiveTheme(
        darkTheme = themeMode.darkTheme,
        dynamicColor = false,
        amoledDark = themeMode.amoledDark
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = M3ExpressiveShape.ExtraLarge
        ) {
            Box(modifier = Modifier.padding(top = 20.dp)) {
                ModeAndModelSheetContent(
                    configOptions = listOf(
                        AcpConfigOption(
                            id = "model",
                            name = "Model",
                            category = "model",
                            currentValue = "gemini-2.5-pro",
                            options = listOf(
                                AcpConfigChoice("gemini-2.5-pro", "Gemini 2.5 Pro", "Deep reasoning and code edits"),
                                AcpConfigChoice("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast responses and quick checks"),
                                AcpConfigChoice("claude-sonnet", "Claude Sonnet", "Balanced coding and review")
                            )
                        )
                    ),
                    usedTokens = 48_000,
                    maxTokens = 200_000,
                    quotaLookup = { value, _ ->
                        when (value) {
                            "gemini-2.5-pro" -> ModelQuotaInfo(value, "Gemini 2.5 Pro", 0.82, null)
                            "gemini-2.5-flash" -> ModelQuotaInfo(value, "Gemini 2.5 Flash", 0.95, null)
                            else -> ModelQuotaInfo(value, "Claude Sonnet", 0.48, null)
                        }
                    },
                    permissionPolicy = "auto",
                    onSetConfigOption = { _, _ -> },
                    onOpenAuth = {},
                    onDismiss = {},
                    isSignedIn = true,
                    connectionState = BridgeConnectionState.CONNECTED,
                    profileName = "Local"
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
