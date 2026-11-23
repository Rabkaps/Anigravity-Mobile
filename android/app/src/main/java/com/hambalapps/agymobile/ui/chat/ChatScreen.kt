package com.hambalapps.agymobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import com.hambalapps.agymobile.ui.components.ContextRingIndicator
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.components.FloatingPillToolbar
import com.hambalapps.agymobile.ui.components.MorphingActionButton
import com.hambalapps.agymobile.ui.components.PrivilegeBadge
import com.hambalapps.agymobile.ui.settings.ConnectionSettingsSheet
import com.hambalapps.agymobile.ui.sheets.AgentsAndMcpSheet
import com.hambalapps.agymobile.ui.sheets.ArtifactReviewSheet
import com.hambalapps.agymobile.ui.sheets.AuthSheet
import com.hambalapps.agymobile.ui.sheets.DiffViewerSheet
import com.hambalapps.agymobile.ui.sheets.ElicitationSheet
import com.hambalapps.agymobile.ui.sheets.ModeAndModelSheet
import com.hambalapps.agymobile.ui.sheets.SessionsDrawerContent
import com.hambalapps.agymobile.ui.sheets.TerminalSheet
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onRequestVoice: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val showSlashPalette = remember(state.composerText) {
        state.composerText.startsWith("/") && !state.composerText.contains(' ')
    }
    val filteredCommands = remember(state.composerText, state.dynamicSlashCommands) {
        if (!showSlashPalette) emptyList()
        else SlashCommandCatalog.filter(state.composerText, state.dynamicSlashCommands)
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionsDrawerContent(
                workspaceCwd = state.workspaceCwd,
                profiles = state.preferences?.profiles.orEmpty(),
                activeProfileId = state.activeProfile?.id,
                sessions = state.savedSessions,
                onNewChat = viewModel::startFreshChat,
                onForkChat = viewModel::forkConversation,
                onSelectProfile = viewModel::selectProfile,
                onChangeWorkspace = viewModel::changeWorkspaceCwd,
                onLoadSession = viewModel::loadSavedSession,
                onOpenSheet = viewModel::openSheet,
                onOpenTerminal = viewModel::openTerminalSheet,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        NavigationRailItem(
                            selected = false,
                            onClick = {
                                viewModel.refreshSessions()
                                scope.launch { drawerState.open() }
                            },
                            icon = { Icon(Icons.Rounded.Menu, contentDescription = "Sessions") },
                            label = { Text("Sessions") }
                        )
                        NavigationRailItem(
                            selected = state.activeSheet == ActiveSheet.MODE_MODEL,
                            onClick = { viewModel.openSheet(ActiveSheet.MODE_MODEL) },
                            icon = { Icon(Icons.Rounded.Tune, contentDescription = "Mode & Model") },
                            label = { Text("Mode") }
                        )
                        NavigationRailItem(
                            selected = state.activeSheet == ActiveSheet.DIFF,
                            onClick = { viewModel.openSheet(ActiveSheet.DIFF) },
                            icon = { Icon(Icons.Rounded.Difference, contentDescription = "Diff") },
                            label = { Text("Diff") }
                        )
                        NavigationRailItem(
                            selected = state.activeSheet == ActiveSheet.AGENTS_MCP,
                            onClick = { viewModel.openSheet(ActiveSheet.AGENTS_MCP) },
                            icon = { Icon(Icons.Rounded.Extension, contentDescription = "MCP") },
                            label = { Text("MCP") }
                        )
                        NavigationRailItem(
                            selected = state.activeSheet == ActiveSheet.TERMINAL,
                            onClick = viewModel::openTerminalSheet,
                            icon = { Icon(Icons.Rounded.Terminal, contentDescription = "Terminal") },
                            label = { Text("Shell") }
                        )
                        NavigationRailItem(
                            selected = state.activeSheet == ActiveSheet.SETTINGS,
                            onClick = { viewModel.openSheet(ActiveSheet.SETTINGS) },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                            label = { Text("Config") }
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        ExpressiveTopBar(
                            state = state,
                            showMenuButton = !isExpanded,
                            onOpenDrawer = {
                                viewModel.refreshSessions()
                                scope.launch { drawerState.open() }
                            },
                            onOpenModeSheet = { viewModel.openSheet(ActiveSheet.MODE_MODEL) },
                            onOpenMcpSheet = { viewModel.openSheet(ActiveSheet.AGENTS_MCP) }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding()
                            .navigationBarsPadding()
                    ) {
                        AnimatedVisibility(
                            visible = state.statusBanner != null,
                            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
                        ) {
                            StatusNoticeBanner(
                                message = state.statusBanner.orEmpty(),
                                showShellAction = state.statusBanner.orEmpty().contains("agy", ignoreCase = true),
                                onReconnect = viewModel::reconnect,
                                onOpenTerminal = viewModel::openTerminalSheet,
                                onOpenSettings = { viewModel.openSheet(ActiveSheet.SETTINGS) }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (state.messages.isEmpty()) {
                                EmptyChatHero(
                                    state = state,
                                    onQuickPrompt = { prompt -> viewModel.sendPrompt(prompt) },
                                    onStartStandaloneEngine = { includeToolchain ->
                                        viewModel.startStandaloneEngine(includeToolchain)
                                    },
                                    onInstallToolchain = viewModel::installStandaloneToolchain,
                                    onAutoSetupBridge = viewModel::autoSetupAndStartBridge,
                                    onOpenTerminal = viewModel::openTerminalSheet,
                                    onOpenSettings = { viewModel.openSheet(ActiveSheet.SETTINGS) }
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 10.dp,
                                        bottom = 16.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.messages, key = { it.id }) { msg ->
                                        MessageBubbleItem(
                                            message = msg,
                                            onOpenDiff = { viewModel.openSheet(ActiveSheet.DIFF) },
                                            modifier = Modifier.animateItem(
                                                placementSpec = M3ExpressiveMotion.adaptiveSpatialDefault()
                                            )
                                        )
                                    }
                                }
                            }

                            SlashCommandPalette(
                                visible = showSlashPalette,
                                commands = filteredCommands,
                                onSelect = viewModel::onSlashCommandPicked,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        FloatingComposerSection(
                            state = state,
                            onTextChange = viewModel::updateComposerText,
                            onSend = { viewModel.sendPrompt() },
                            onStop = viewModel::cancelTurn,
                            onSlashTap = { viewModel.updateComposerText("/") },
                            onVoiceTap = onRequestVoice,
                            onOpenModeSheet = { viewModel.openSheet(ActiveSheet.MODE_MODEL) },
                            onOpenDiffSheet = { viewModel.openSheet(ActiveSheet.DIFF) },
                            onOpenArtifactSheet = { viewModel.openSheet(ActiveSheet.ARTIFACT) },
                            onOpenTerminal = viewModel::openTerminalSheet
                        )
                    }
                }
            }
        }
    }

    // Interactive Sheets, Auth, Elicitation & Permission Prompts
    state.currentPermission?.let { perm ->
        PermissionRequestSheet(
            request = perm,
            permissionPolicy = state.preferences?.permissionPolicy ?: "request_review",
            onSelectPolicy = viewModel::setPermissionPolicy,
            onRespond = { optionId -> viewModel.respondPermission(perm, optionId) }
        )
    }

    state.currentElicitation?.let { elicitation ->
        ElicitationSheet(
            request = elicitation,
            onRespond = { action, content -> viewModel.respondElicitation(elicitation, action, content) }
        )
    }

    when (state.activeSheet) {
        ActiveSheet.AUTH -> AuthSheet(
            initializeResult = state.initializeResult,
            authInProgress = state.authInProgress,
            authError = state.authError,
            onSelectAuthMethod = viewModel::authenticateWithMethod,
            onSubmitGeminiApiKey = viewModel::submitGeminiApiKey,
            onOpenTerminalLogin = viewModel::openTerminalForAuth,
            onRetryHandshake = viewModel::retryAcpHandshake,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.MODE_MODEL -> ModeAndModelSheet(
            configOptions = state.configOptions,
            usedTokens = state.usedTokens,
            maxTokens = state.maxTokens,
            quotaSummary = state.quotaSummary,
            isLoadingQuota = state.isLoadingQuota,
            quotaLookup = state::quotaForModelChoice,
            onRefreshQuota = viewModel::refreshQuota,
            permissionPolicy = state.preferences?.permissionPolicy ?: "request_review",
            onSelectPermissionPolicy = viewModel::setPermissionPolicy,
            onSetConfigOption = viewModel::setConfigOption,
            onOpenAuth = { viewModel.openSheet(ActiveSheet.AUTH) },
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.DIFF -> DiffViewerSheet(
            files = state.diffFiles,
            onRefresh = viewModel::refreshDiff,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.ARTIFACT -> ArtifactReviewSheet(
            artifacts = state.artifacts,
            selectedArtifactId = state.selectedArtifactId,
            onDecision = viewModel::approveArtifact,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.AGENTS_MCP -> AgentsAndMcpSheet(
            health = state.health,
            bridgeSettingsJson = state.bridgeSettingsJson,
            initializeResult = state.initializeResult,
            subagents = state.activeSubagents,
            onOpenTerminal = {
                viewModel.closeSheet()
                viewModel.openTerminalSheet()
            },
            onRunAgentsTerminal = {
                viewModel.closeSheet()
                viewModel.openTerminalWithCommand("agy agents")
            },
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.TERMINAL -> TerminalSheet(
            ptyClient = viewModel.ptyClient,
            pendingCommand = state.terminalPendingCommand,
            openedForAuth = state.openedTerminalForAuth,
            onConsumedPendingCommand = viewModel::clearTerminalPendingCommand,
            onDoneAuthReconnect = viewModel::finishTerminalAuthAndReconnect,
            onOpenUrl = viewModel::openUrlInBrowser,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.SETTINGS -> ConnectionSettingsSheet(
            preferences = state.preferences,
            testResult = state.connectionTestResult,
            isBootstrapping = state.isBootstrapping,
            bootstrapStatus = state.bootstrapStatus,
            standaloneInstalled = state.standaloneInstalled,
            standaloneToolchainInstalled = state.standaloneToolchainInstalled,
            standaloneProgress = state.standaloneProgress,
            detectedAbi = state.detectedAbi,
            onStartStandaloneEngine = { includeToolchain ->
                viewModel.startStandaloneEngine(includeToolchain)
            },
            onInstallToolchain = viewModel::installStandaloneToolchain,
            onAutoSetupBridge = viewModel::autoSetupAndStartBridge,
            onSaveProfile = viewModel::saveProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onSelectProfile = viewModel::selectProfile,
            onTestConnection = viewModel::testConnection,
            onToggleAmoled = viewModel::setAmoledDark,
            onToggleDynamicColor = viewModel::setDynamicColor,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.NONE -> Unit
    }
}

@Composable
internal fun ExpressiveTopBar(
    state: ChatUiState,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModeSheet: () -> Unit,
    onOpenMcpSheet: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showMenuButton) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Open drawer")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(M3ExpressiveShape.Large)
                    .clickable(onClick = onOpenModeSheet)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.activeProfile?.name ?: "Agy Mobile",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitleParts = buildList {
                    add(state.activeModeLabel)
                    if (state.activeEffortLabel.isNotBlank()) add(state.activeEffortLabel)
                    add(state.activeModelLabel)
                }
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PrivilegeBadge(
                privilegeLevel = state.health?.privilegeLevel ?: "none",
                connectionState = state.connectionState,
                isRemote = state.activeProfile?.isRemote ?: false,
                onClick = onOpenMcpSheet
            )

            ContextRingIndicator(
                usageRatio = state.contextUsageRatio,
                onClick = onOpenModeSheet
            )
        }
    }
}

@Composable
private fun StatusNoticeBanner(
    message: String,
    showShellAction: Boolean,
    onReconnect: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onReconnect) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Reconnect",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (showShellAction) {
                TextButton(onClick = onOpenTerminal) {
                    Text("Shell", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text("Setup", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyChatHero(
    state: ChatUiState,
    onQuickPrompt: (String) -> Unit,
    onStartStandaloneEngine: (includeToolchain: Boolean) -> Unit = {},
    onInstallToolchain: () -> Unit = {},
    onAutoSetupBridge: (useRootSu: Boolean) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = M3ExpressiveShape.ExtraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Antigravity on Android",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        val abiNote = if (state.detectedAbi.isNotBlank()) " (${state.detectedAbi})" else ""
        Text(
            text = "Powered by official agy acp$abiNote with ${state.health?.privilegeLevel ?: "standalone"} device control.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (state.connectionState != BridgeConnectionState.CONNECTED || state.isBootstrapping) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.ExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Standalone Built-In Engine",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Runs official agy directly inside Agy Mobile on arm64-v8a & x86_64 (gbOS86) — no Termux app required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isBootstrapping) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveLoadingIndicator()
                                Text(
                                    text = state.standaloneStatus ?: state.bootstrapStatus ?: "Starting…",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            state.standaloneProgress?.let { prog ->
                                LinearProgressIndicator(
                                    progress = { prog.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    } else if ((state.standaloneStatus ?: state.bootstrapStatus) != null) {
                        Text(
                            text = state.standaloneStatus ?: state.bootstrapStatus.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { onStartStandaloneEngine(false) },
                        enabled = !state.isBootstrapping,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = M3ExpressiveShape.Full
                    ) {
                        Text(
                            if (state.standaloneInstalled) "Start Standalone Engine"
                            else "Install & Start Standalone Engine"
                        )
                    }

                    if (!state.standaloneToolchainInstalled) {
                        FilledTonalButton(
                            onClick = {
                                if (state.standaloneInstalled) onInstallToolchain()
                                else onStartStandaloneEngine(true)
                            },
                            enabled = !state.isBootstrapping,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.Full
                        ) {
                            Text("Install Git & Node/NPM Toolchain")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onAutoSetupBridge(false) },
                            enabled = !state.isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupLeftShape
                        ) {
                            Text("Termux")
                        }
                        FilledTonalButton(
                            onClick = { onAutoSetupBridge(true) },
                            enabled = !state.isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupRightShape
                        ) {
                            Text("Termux Root (su)")
                        }
                    }

                    if (state.termuxInstalled) {
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Termux allow-external-apps",
                                        TermuxBridgeBootstrap.ENABLE_EXTERNAL_APPS_CMD
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    "Copied 1-time Termux permission command",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Copy 1-time unrooted Termux enable command")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onQuickPrompt("Check Android status and tell me what app is in the foreground.") },
                label = { Text("Inspect Device") }
            )
            AssistChip(
                onClick = { onQuickPrompt("Take a screenshot of my screen and describe it.") },
                label = { Text("Screen Vision") }
            )
            AssistChip(
                onClick = { onQuickPrompt("/plan ") },
                label = { Text("/plan") }
            )
            AssistChip(
                onClick = onOpenTerminal,
                label = { Text("Login / Terminal") }
            )
            AssistChip(
                onClick = onOpenSettings,
                label = { Text("Bridge Setup") }
            )
        }
    }
}

@Composable
internal fun MessageBubbleItem(
    message: ChatMessageUi,
    onOpenDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (message.role) {
        MessageRole.USER -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = M3ExpressiveShape.ExtraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
        MessageRole.SYSTEM -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
        MessageRole.AGENT -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.thinking.isNotBlank()) {
                    ThinkingPill(thinkingText = message.thinking)
                }

                if (message.planSteps.isNotEmpty()) {
                    Surface(
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Agent Plan", style = MaterialTheme.typography.labelLarge)
                            message.planSteps.forEach { step ->
                                Text(
                                    text = "• $step",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                message.toolCalls.forEach { tool ->
                    ToolCallCard(tool = tool, onOpenDiff = onOpenDiff)
                }

                if (message.text.isNotBlank()) {
                    Surface(
                        shape = M3ExpressiveShape.ExtraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        MarkdownText(
                            markdown = message.text,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        ExpressiveLoadingIndicator()
                        Text(
                            text = "agy is working…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingPill(thinkingText: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = M3ExpressiveShape.LargeIncreased,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(tool: ToolCallUi, onOpenDiff: () -> Unit) {
    var expanded by remember { mutableStateOf(tool.imageBase64 != null) }

    Surface(
        onClick = { expanded = !expanded },
        shape = M3ExpressiveShape.LargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (tool.status == "completed") Icons.Rounded.CheckCircle else Icons.Rounded.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (tool.status == "completed") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = tool.status,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Inline screenshot preview from `android_screenshot`
            tool.imageBase64?.let { b64 ->
                val bitmap = remember(b64) {
                    runCatching {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Android screen capture",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(M3ExpressiveShape.Large)
                    )
                }
            }

            if (tool.diffPath != null) {
                TextButton(onClick = onOpenDiff) {
                    Text("Inspect diff: ${tool.diffPath}")
                }
            }

            AnimatedVisibility(
                visible = expanded && tool.textOutput.isNotBlank(),
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Surface(
                    shape = M3ExpressiveShape.Medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = tool.textOutput.take(2000),
                        style = MonoCodeStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingComposerSection(
    state: ChatUiState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSlashTap: () -> Unit,
    onVoiceTap: () -> Unit,
    onOpenModeSheet: () -> Unit,
    onOpenDiffSheet: () -> Unit,
    onOpenArtifactSheet: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Expressive Floating Toolbar Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingPillToolbar {
                TextButton(onClick = onSlashTap) {
                    Text("/", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                }
                TextButton(onClick = onOpenModeSheet) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(state.activeModeLabel)
                }
                TextButton(onClick = onOpenDiffSheet) {
                    Icon(Icons.Rounded.Difference, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Diff (${state.diffFiles.size})")
                }
                if (state.artifacts.isNotEmpty()) {
                    TextButton(onClick = onOpenArtifactSheet) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Artifact")
                    }
                }
                TextButton(onClick = onOpenTerminal) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Shell")
                }
            }
        }

        // Floating Pill Composer Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = M3ExpressiveShape.ExtraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 140.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (state.composerText.isEmpty()) {
                        Text(
                            text = "Ask agy or type / for commands…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = state.composerText,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    onClick = onVoiceTap,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Voice dictation",
                        tint = if (state.voiceListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                MorphingActionButton(
                    isStreaming = state.isStreaming,
                    enabled = state.composerText.isNotBlank(),
                    onSend = onSend,
                    onStop = onStop
                )
            }
        }
    }
}
