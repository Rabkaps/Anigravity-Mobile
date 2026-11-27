package com.hambalapps.agymobile.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.components.AntigravityHeroEmblem
import com.hambalapps.agymobile.ui.components.AntigravityStarfieldBackground
import com.hambalapps.agymobile.ui.components.BridgeConnectionState
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.components.MorphingActionButton
import com.hambalapps.agymobile.ui.settings.ConnectionSettingsSheet
import com.hambalapps.agymobile.ui.sheets.AgentsAndMcpSheet
import com.hambalapps.agymobile.ui.sheets.ArtifactReviewSheet
import com.hambalapps.agymobile.ui.sheets.AuthSheet
import com.hambalapps.agymobile.ui.sheets.DiffViewerSheet
import com.hambalapps.agymobile.ui.sheets.ElicitationSheet
import com.hambalapps.agymobile.ui.sheets.ModeAndModelSheet
import com.hambalapps.agymobile.ui.sheets.SessionsDrawerContent
import com.hambalapps.agymobile.ui.sheets.TasksDrawerContent
import com.hambalapps.agymobile.ui.sheets.TerminalSheet
import com.hambalapps.agymobile.ui.theme.LocalReducedMotion
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onRequestVoice: () -> Unit,
    onAttachImages: () -> Unit = {},
    onAttachFiles: () -> Unit = {},
    onPickSystemFolder: ((String) -> Unit) -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val tasksDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val showSlashPalette = remember(state.composerText) {
        state.composerText.startsWith("/") && !state.composerText.contains(' ')
    }
    val filteredCommands = remember(state.composerText, state.dynamicSlashCommands) {
        if (!showSlashPalette) emptyList()
        else SlashCommandCatalog.filter(state.composerText, state.dynamicSlashCommands)
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                ChatViewModel.UiEffect.OpenSessionsDrawer -> drawerState.open()
                ChatViewModel.UiEffect.LaunchVoiceInput -> Unit
            }
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            viewModel.refreshSessions()
        }
    }

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) true
            else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (isNearBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.messages.lastOrNull()?.text?.length) {
        if (isNearBottom && state.messages.isNotEmpty() && state.messages.lastOrNull()?.isStreaming == true) {
            listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = tasksDrawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    TasksDrawerContent(
                        state = state,
                        onStopTurn = viewModel::cancelTurn,
                        onOpenDiff = {
                            scope.launch { tasksDrawerState.close() }
                            viewModel.openSheet(ActiveSheet.DIFF)
                        },
                        onClose = { scope.launch { tasksDrawerState.close() } }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
        drawerContent = {
            SessionsDrawerContent(
                workspaceCwd = state.workspaceCwd,
                engineHomePath = viewModel.engineHomePath,
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
                onCloseDrawer = { scope.launch { drawerState.close() } },
                isSignedIn = state.standaloneSignedIn || state.sessionId != null,
                onLogout = viewModel::logoutFromAgent,
                projects = state.preferences?.projects.orEmpty(),
                activeProjectId = state.preferences?.activeProjectId,
                onSelectProject = viewModel::selectProject,
                onSaveProject = viewModel::saveProject,
                onDeleteProject = viewModel::deleteProject,
                onPickSystemFolder = onPickSystemFolder
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
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        ExpressiveTopBar(
                            state = state,
                            showMenuButton = !isExpanded,
                            onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            },
                            onOpenTasksDrawer = {
                                scope.launch { tasksDrawerState.open() }
                            },
                            onOpenModeSheet = { viewModel.openSheet(ActiveSheet.MODE_MODEL) },
                            onNewChat = viewModel::startFreshChat
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
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

                        AnimatedVisibility(
                            visible = state.pendingBrowserUrl != null,
                            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
                        ) {
                            BrowserUrlBanner(
                                url = state.pendingBrowserUrl.orEmpty(),
                                onOpen = {
                                    val url = state.pendingBrowserUrl
                                    if (!url.isNullOrBlank()) {
                                        viewModel.openUrlInBrowser(url, force = true)
                                    }
                                    viewModel.dismissPendingBrowserUrl()
                                },
                                onDismiss = viewModel::dismissPendingBrowserUrl
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
                                    onPrefillPrompt = viewModel::updateComposerText,
                                    onStartStandaloneEngine = { includeToolchain ->
                                        viewModel.startStandaloneEngine(includeToolchain)
                                    },
                                    onUpdateStandaloneAgy = viewModel::updateStandaloneAgy,
                                    onInstallToolchain = viewModel::installStandaloneToolchain,
                                    onOpenAuth = { viewModel.openSheet(ActiveSheet.AUTH) },
                                    onOpenMarketplace = { viewModel.openSheet(ActiveSheet.AGENTS_MCP) },
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

                        AnimatedVisibility(
                            visible = state.btwSideNote != null,
                            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
                        ) {
                            state.btwSideNote?.let { note ->
                                BtwSideNoteCard(
                                    note = note,
                                    onDismiss = viewModel::dismissBtwSideNote
                                )
                            }
                        }

                        FloatingComposerSection(
                            state = state,
                            onTextChange = viewModel::updateComposerText,
                            onSend = { viewModel.sendPrompt() },
                            onStop = viewModel::cancelTurn,
                            onSendQueuedMessageNow = viewModel::sendQueuedMessageNow,
                            onRemoveQueuedMessage = viewModel::removeQueuedMessage,
                            onAttachImages = onAttachImages,
                            onAttachFiles = onAttachFiles,
                            onRemoveAttachment = viewModel::removeAttachment,
                            onSlashTap = { viewModel.updateComposerText("/") },
                            onVoiceTap = onRequestVoice,
                            onOpenModeSheet = { viewModel.openSheet(ActiveSheet.MODE_MODEL) },
                            onOpenDiffSheet = { viewModel.openSheet(ActiveSheet.DIFF) },
                            onOpenArtifactSheet = { viewModel.openSheet(ActiveSheet.ARTIFACT) },
                            onOpenTerminal = viewModel::openTerminalSheet,
                            onOpenTasksDrawer = { scope.launch { tasksDrawerState.open() } }
                        )
                    }
                }
            }
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
            onDismiss = viewModel::closeSheet,
            isStandalone = state.activeProfile?.isStandalone ?: true,
            standaloneToolchainInstalled = state.standaloneToolchainInstalled,
            googleLoginState = state.googleLoginState,
            onStartGuidedGoogleLogin = viewModel::startGuidedGoogleLogin,
            onSubmitGoogleAuthCode = viewModel::submitGoogleAuthCode,
            onCancelGuidedGoogleLogin = viewModel::cancelGuidedGoogleLogin,
            onOpenBrowserUrl = { url -> viewModel.openUrlInBrowser(url, force = true) },
            onInstallToolchain = viewModel::installStandaloneToolchain,
            isSignedIn = state.standaloneSignedIn || state.sessionId != null,
            onLogout = viewModel::logoutFromAgent
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
            onDismiss = viewModel::closeSheet,
            isSignedIn = state.standaloneSignedIn || state.sessionId != null,
            onLogout = viewModel::logoutFromAgent,
            connectionState = state.connectionState,
            profileName = state.activeProfile?.name ?: "Local",
            onOpenSettings = { viewModel.openSheet(ActiveSheet.SETTINGS) }
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
            onDismiss = viewModel::closeSheet,
            isStandalone = state.activeProfile?.isStandalone ?: true,
            standaloneToolchainInstalled = state.standaloneToolchainInstalled,
            marketplaceCatalog = state.marketplaceCatalog,
            installedPlugins = state.installedPlugins,
            isLoadingPlugins = state.isLoadingPlugins,
            pluginActionTarget = state.pluginActionTarget,
            pluginStatusMessage = state.pluginStatusMessage,
            globalRulesText = state.globalRulesText,
            workspaceRulesText = state.workspaceRulesText,
            discoveredSkills = state.discoveredSkills,
            onRefreshPlugins = viewModel::refreshMarketplaceAndPlugins,
            onInstallPlugin = viewModel::installPlugin,
            onTogglePlugin = viewModel::setPluginEnabled,
            onUninstallPlugin = viewModel::uninstallPlugin,
            onSaveRules = viewModel::saveRules,
            onSaveSkill = viewModel::createOrSaveSkill,
            onUpdateCliSettingBoolean = viewModel::updateCliSettingBoolean,
            onInstallToolchain = viewModel::installStandaloneToolchain
        )
        ActiveSheet.TERMINAL -> TerminalSheet(
            ptyClient = viewModel.ptyClient,
            pendingCommand = state.terminalPendingCommand,
            openedForAuth = state.openedTerminalForAuth,
            onConsumedPendingCommand = viewModel::clearTerminalPendingCommand,
            onDoneAuthReconnect = viewModel::finishTerminalAuthAndReconnect,
            onOpenUrl = viewModel::openUrlInBrowser,
            onDismiss = viewModel::closeSheet,
            isStandalone = state.activeProfile?.isStandalone ?: false,
            standaloneToolchainInstalled = state.standaloneToolchainInstalled,
            onUpdateStandaloneAgy = viewModel::updateStandaloneAgy,
            onInstallToolchain = viewModel::installStandaloneToolchain
        )
        ActiveSheet.SETTINGS -> ConnectionSettingsSheet(
            preferences = state.preferences,
            testResult = state.connectionTestResult,
            isBootstrapping = state.isBootstrapping,
            bootstrapStatus = state.bootstrapStatus,
            standaloneInstalled = state.standaloneInstalled,
            standaloneToolchainInstalled = state.standaloneToolchainInstalled,
            standaloneAndroidSdkInstalled = state.standaloneAndroidSdkInstalled,
            standaloneProgress = state.standaloneProgress,
            detectedAbi = state.detectedAbi,
            onStartStandaloneEngine = { includeToolchain ->
                viewModel.startStandaloneEngine(includeToolchain)
            },
            onUpdateStandaloneAgy = viewModel::updateStandaloneAgy,
            onInstallToolchain = viewModel::installStandaloneToolchain,
            onInstallAndroidSdk = viewModel::installAndroidBuildSdk,
            onSaveProfile = viewModel::saveProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onSelectProfile = viewModel::selectProfile,
            onTestConnection = viewModel::testConnection,
            onToggleAmoled = viewModel::setAmoledDark,
            onToggleDynamicColor = viewModel::setDynamicColor,
            onSelectThemeMode = viewModel::setThemeMode,
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
    onOpenTasksDrawer: () -> Unit = {},
    onOpenModeSheet: () -> Unit,
    onNewChat: () -> Unit = {}
) {
    val statusDotColor = when (state.connectionState) {
        BridgeConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
        BridgeConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
        BridgeConnectionState.ERROR -> MaterialTheme.colorScheme.error
        BridgeConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    val modelPillLabel = state.activeModelLabel
        .takeIf { it != "connect to load" }
        ?: (state.activeProfile?.name ?: "Antigravity")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMenuButton) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Open drawer")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onOpenModeSheet,
                    shape = M3ExpressiveShape.Full,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = modelPillLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "Select model and mode",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val activeTaskCount = state.runningTasks.size + state.activeSubagents.count {
                it.status.equals("running", ignoreCase = true) ||
                    it.status.equals("in_progress", ignoreCase = true)
            }
            Box {
                IconButton(onClick = onOpenTasksDrawer, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Tasks and logs",
                        tint = if (activeTaskCount > 0 || state.isStreaming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (activeTaskCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            IconButton(onClick = onNewChat, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "New chat")
            }
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

@Composable
private fun BrowserUrlBanner(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onOpen,
                shape = M3ExpressiveShape.Full,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Open")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BtwSideNoteCard(
    note: BtwSideNoteUi,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = M3ExpressiveShape.Full,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "/btw",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = note.question,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss side note",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (note.isLoading && note.answer.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveLoadingIndicator()
                    Text(
                        text = "Answering side question…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (note.answer.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(markdown = note.answer)
                }
            }
            if (!note.error.isNullOrBlank()) {
                Text(
                    text = note.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun EmptyChatHero(
    state: ChatUiState,
    onQuickPrompt: (String) -> Unit,
    onPrefillPrompt: (String) -> Unit = onQuickPrompt,
    onStartStandaloneEngine: (includeToolchain: Boolean) -> Unit = {},
    onUpdateStandaloneAgy: () -> Unit = {},
    onInstallToolchain: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AntigravityStarfieldBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AntigravityHeroEmblem()
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Antigravity",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))

            val needsConnection = state.connectionState != BridgeConnectionState.CONNECTED
            val needsAuth = state.activeProfile?.isStandalone == true && !state.standaloneSignedIn
            val showSetupCard = state.isBootstrapping || needsConnection || needsAuth

            if (showSetupCard) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.isBootstrapping) {
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
                        } else if (needsConnection) {
                            Button(
                                onClick = { onStartStandaloneEngine(false) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full
                            ) {
                                Text(
                                    if (state.standaloneInstalled) "Start local engine"
                                    else "Set up local engine"
                                )
                            }
                            TextButton(onClick = onOpenSettings) {
                                Text("Connection options")
                            }
                        } else if (needsAuth) {
                            Button(
                                onClick = onOpenAuth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full
                            ) {
                                Text("Sign in with Google")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Rounded.Build,
                    title = "Inspect device",
                    subtitle = "Check current screen and active app",
                    onClick = { onQuickPrompt("Check Android status and tell me what app is in the foreground.") }
                )
                QuickActionCard(
                    icon = Icons.Rounded.Description,
                    title = "Plan a task",
                    subtitle = "Outline steps before editing files",
                    onClick = { onPrefillPrompt("/plan ") }
                )
                QuickActionCard(
                    icon = Icons.Rounded.Extension,
                    title = "Plugins & MCP",
                    subtitle = "Manage tools, rules, and extensions",
                    onClick = onOpenMarketplace
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.ExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = M3ExpressiveShape.Medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
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
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 6.dp,
                        bottomStart = 22.dp,
                        bottomEnd = 22.dp
                    ),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = 312.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.attachmentLabels.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                message.attachmentLabels.forEach { label ->
                                    Surface(
                                        shape = M3ExpressiveShape.Medium,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDirection = detectTextDirection(message.text)
                            )
                        )
                    }
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = detectTextDirection(step)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                message.toolCalls.forEach { tool ->
                    key(tool.toolCallId) {
                        ToolCallCard(tool = tool, onOpenDiff = onOpenDiff)
                    }
                }

                if (message.text.isNotBlank()) {
                    MarkdownText(
                        markdown = message.text,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        ExpressiveLoadingIndicator()
                        Text(
                            text = "Working…",
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = detectTextDirection(thinkingText)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(tool: ToolCallUi, onOpenDiff: () -> Unit) {
    var expanded by remember { mutableStateOf(tool.imageBase64 != null) }
    val isDone = tool.status == "completed"
    val iconTint = if (isDone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Surface(
        onClick = { expanded = !expanded },
        shape = M3ExpressiveShape.LargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = iconTint
                            )
                        } else {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = iconTint
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tool.diffPath != null) {
                        Text(
                            text = tool.diffPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (tool.diffPath != null) {
                    Surface(
                        onClick = onOpenDiff,
                        shape = M3ExpressiveShape.Full,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "View diff",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = tool.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inline screenshot preview from `android_screenshot`
            tool.imageBase64?.let { b64 ->
                val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = b64) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            var sample = 1
                            while ((bounds.outWidth / sample) > 1080 || (bounds.outHeight / sample) > 1080) {
                                sample *= 2
                            }
                            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                bitmap?.let { decoded ->
                    Image(
                        bitmap = decoded,
                        contentDescription = "Android screen capture",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(M3ExpressiveShape.Large)
                    )
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
                    val linkColor = MaterialTheme.colorScheme.primary
                    val linkedOutput = remember(tool.textOutput, linkColor) {
                        linkifyPlainText(tool.textOutput.take(2000), linkColor)
                    }
                    Text(
                        text = linkedOutput,
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
    onSendQueuedMessageNow: (String) -> Unit = {},
    onRemoveQueuedMessage: (String) -> Unit = {},
    onAttachImages: () -> Unit = {},
    onAttachFiles: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onSlashTap: () -> Unit,
    onVoiceTap: () -> Unit,
    onOpenModeSheet: () -> Unit,
    onOpenDiffSheet: () -> Unit,
    onOpenArtifactSheet: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenTasksDrawer: () -> Unit = {}
) {
    var toolsExpanded by remember { mutableStateOf(false) }
    val activeSubagents = remember(state.activeSubagents) {
        state.activeSubagents.filter {
            it.status.equals("running", ignoreCase = true) ||
                it.status.equals("in_progress", ignoreCase = true)
        }
    }
    val hasRunningTasks = state.runningTasks.isNotEmpty() || activeSubagents.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = hasRunningTasks,
            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
        ) {
            RunningTasksPill(
                runningTools = state.runningTasks,
                runningSubagents = activeSubagents,
                onStop = onStop,
                onOpenTasksDrawer = onOpenTasksDrawer
            )
        }

        AnimatedVisibility(
            visible = state.queuedMessages.isNotEmpty(),
            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
        ) {
            QueuedMessagesStrip(
                queuedMessages = state.queuedMessages,
                onSendNow = onSendQueuedMessageNow,
                onRemove = onRemoveQueuedMessage
            )
        }
        AnimatedVisibility(
            visible = state.pendingAttachments.isNotEmpty(),
            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
        ) {
            PendingAttachmentsStrip(
                attachments = state.pendingAttachments,
                onRemove = onRemoveAttachment
            )
        }
        AnimatedVisibility(
            visible = toolsExpanded,
            enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
            exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ComposerToolPill(
                    label = "Photo",
                    icon = Icons.Rounded.Image,
                    onClick = {
                        toolsExpanded = false
                        onAttachImages()
                    }
                )
                ComposerToolPill(
                    label = "File",
                    icon = Icons.Rounded.AttachFile,
                    onClick = {
                        toolsExpanded = false
                        onAttachFiles()
                    }
                )
                ComposerToolPill(
                    label = "Commands /",
                    onClick = {
                        toolsExpanded = false
                        onSlashTap()
                    }
                )
                ComposerToolPill(
                    label = "Diff (${state.diffFiles.size})",
                    icon = Icons.Rounded.Difference,
                    onClick = {
                        toolsExpanded = false
                        onOpenDiffSheet()
                    }
                )
                if (state.artifacts.isNotEmpty()) {
                    ComposerToolPill(
                        label = "Artifacts",
                        icon = Icons.Rounded.Description,
                        onClick = {
                            toolsExpanded = false
                            onOpenArtifactSheet()
                        }
                    )
                }
                ComposerToolPill(
                    label = "Terminal",
                    icon = Icons.Rounded.Terminal,
                    onClick = {
                        toolsExpanded = false
                        onOpenTerminal()
                    }
                )
                ComposerToolPill(
                    label = state.activeModeLabel,
                    icon = Icons.Rounded.Tune,
                    onClick = {
                        toolsExpanded = false
                        onOpenModeSheet()
                    }
                )
            }
        }

        // Single Unified Pill Composer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = M3ExpressiveShape.ExtraLargeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { toolsExpanded = !toolsExpanded },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (toolsExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = if (toolsExpanded) "Hide tools" else "Tools",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 140.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (state.composerText.isEmpty()) {
                        Text(
                            text = "Message Antigravity…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = state.composerText,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textDirection = detectTextDirection(state.composerText)
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

                val canSend = state.composerText.isNotBlank() || state.pendingAttachments.isNotEmpty()
                if ((state.isStreaming || hasRunningTasks) && canSend) {
                    FilledTonalIconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Queue message"
                        )
                    }
                }

                MorphingActionButton(
                    isStreaming = state.isStreaming || hasRunningTasks,
                    enabled = canSend,
                    onSend = onSend,
                    onStop = onStop
                )
            }
        }
    }
}

@Composable
private fun RunningTasksPill(
    runningTools: List<ToolCallUi>,
    runningSubagents: List<com.hambalapps.agymobile.data.acp.AcpSubagentInfo>,
    onStop: () -> Unit = {},
    onOpenTasksDrawer: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val totalCount = runningTools.size + runningSubagents.size
    val primaryLabel = runningTools.firstOrNull()?.title
        ?: runningSubagents.firstOrNull()?.role
        ?: "Running task"

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.LargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = if (totalCount == 1) {
                        "1 task running · $primaryLabel"
                    } else {
                        "$totalCount tasks running"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onOpenTasksDrawer,
                    shape = M3ExpressiveShape.Full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop running tasks",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse tasks" else "Expand tasks",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    runningTools.forEach { tool ->
                        Text(
                            text = "• ${tool.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    runningSubagents.forEach { agent ->
                        Text(
                            text = "• ${agent.role}: ${agent.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedMessagesStrip(
    queuedMessages: List<QueuedMessageUi>,
    onSendNow: (String) -> Unit = {},
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        queuedMessages.forEach { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = detectTextDirection(item.text)
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onSendNow(item.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send queued message now",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove queued message",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentsStrip(
    attachments: List<PendingAttachmentUi>,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { item ->
            Surface(
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (item.isImage) Icons.Rounded.Image else Icons.Rounded.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove attachment",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerToolPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = M3ExpressiveShape.Full,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
