package com.hambalapps.agymobile.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpSubagentInfo
import com.hambalapps.agymobile.ui.chat.ChatUiState
import com.hambalapps.agymobile.ui.chat.ToolCallUi
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle

private val TERMINAL_STATUSES = setOf("completed", "failed", "error", "cancelled", "done")

@Composable
fun TasksDrawerContent(
    state: ChatUiState,
    onStopTurn: () -> Unit,
    onOpenDiff: () -> Unit,
    onClose: () -> Unit
) {
    val allTools = state.allToolCalls
    val runningTools = state.runningTasks
    val completedTools = remember(allTools) {
        allTools.filter { it.status.lowercase() in TERMINAL_STATUSES }
    }
    val runningSubagents = remember(state.activeSubagents) {
        state.activeSubagents.filter { it.status.lowercase() !in TERMINAL_STATUSES }
    }
    val runningCount = runningTools.size + runningSubagents.size
    val completedCount = completedTools.size + (state.activeSubagents.size - runningSubagents.size)
    val isActive = state.isStreaming || runningCount > 0

    var selectedFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Running, 2=Done
    val filteredTools = remember(allTools, selectedFilter) {
        val base = when (selectedFilter) {
            1 -> allTools.filter { it.status.lowercase() !in TERMINAL_STATUSES }
            2 -> allTools.filter { it.status.lowercase() in TERMINAL_STATUSES }
            else -> allTools
        }
        val activeFirst = base.filter { it.status.lowercase() !in TERMINAL_STATUSES }
        val doneReversed = base.filter { it.status.lowercase() in TERMINAL_STATUSES }.asReversed()
        activeFirst + doneReversed
    }
    val filteredSubagents = remember(state.activeSubagents, selectedFilter) {
        when (selectedFilter) {
            1 -> state.activeSubagents.filter { it.status.lowercase() !in TERMINAL_STATUSES }
            2 -> state.activeSubagents.filter { it.status.lowercase() in TERMINAL_STATUSES }
            else -> state.activeSubagents
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = M3ExpressiveShape.ExtraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tasks & Progress",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (runningCount == 0 && completedCount == 0) {
                            if (state.isStreaming) "Agent streaming…" else "Idle"
                        } else {
                            "$runningCount running · $completedCount done"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (runningCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (isActive) {
                    FilledTonalButton(
                        onClick = onStopTurn,
                        shape = M3ExpressiveShape.Full
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Stop")
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close tasks menu"
                    )
                }
            }

            val planSteps = state.latestPlanSteps
            if (planSteps.isNotEmpty() || allTools.isNotEmpty()) {
                TasksProgressCard(
                    planSteps = planSteps,
                    totalTools = allTools.size,
                    doneTools = completedTools.size,
                    isRunning = runningCount > 0 || state.isStreaming
                )
            }

            ConnectedButtonGroup(
                options = listOf("All", "Running", "Done"),
                selectedIndex = selectedFilter,
                onSelect = { selectedFilter = it },
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isStreaming && state.activeThinkingText.isNotBlank() && selectedFilter != 2) {
                    item(key = "__live_thinking__") {
                        LiveReasoningLogCard(thinkingText = state.activeThinkingText)
                    }
                }

                if (filteredSubagents.isNotEmpty()) {
                    itemsIndexed(
                        items = filteredSubagents,
                        key = { idx, sub -> "sub:${sub.id}:$idx" }
                    ) { _, sub ->
                        SubagentTaskCard(subagent = sub)
                    }
                }

                if (filteredTools.isEmpty() && filteredSubagents.isEmpty() && !(state.isStreaming && state.activeThinkingText.isNotBlank() && selectedFilter != 2)) {
                    item(key = "__empty_tasks__") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = when (selectedFilter) {
                                    1 -> "No tasks currently running."
                                    2 -> "No completed tasks yet."
                                    else -> "Tool runs, subagents, and live command outputs will appear here."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredTools,
                        key = { idx, tool -> "tool:${tool.toolCallId}:$idx" }
                    ) { _, tool ->
                        TaskLogCard(
                            tool = tool,
                            onOpenDiff = onOpenDiff
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksProgressCard(
    planSteps: List<String>,
    totalTools: Int,
    doneTools: Int,
    isRunning: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (planSteps.isNotEmpty()) {
                val doneSteps = planSteps.count { step ->
                    val lower = step.lowercase()
                    lower.startsWith("[completed]") || lower.startsWith("[done]")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plan Progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$doneSteps / ${planSteps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { (doneSteps.toFloat() / planSteps.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    planSteps.take(6).forEach { step ->
                        val lower = step.lowercase()
                        val isDone = lower.startsWith("[completed]") || lower.startsWith("[done]")
                        val isInProgress = lower.startsWith("[in_progress]") || lower.startsWith("[running]")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (isInProgress) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = step.replace(Regex("^\\[[^\\]]+\\]\\s*"), ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tool Execution",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$doneTools / $totalTools completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isRunning && doneTools == 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { (doneTools.toFloat() / totalTools.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveReasoningLogCard(thinkingText: String) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(thinkingText.length, expanded) {
        if (expanded) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Live Reasoning",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = thinkingText.takeLast(4000),
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(scrollState)
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubagentTaskCard(subagent: AcpSubagentInfo) {
    val isRunning = subagent.status.lowercase() !in TERMINAL_STATUSES
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunning) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subagent.name.ifBlank { subagent.id },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subagent.role.isNotBlank()) {
                    Text(
                        text = subagent.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = M3ExpressiveShape.Full,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = subagent.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskLogCard(
    tool: ToolCallUi,
    onOpenDiff: () -> Unit
) {
    val statusLower = tool.status.lowercase()
    val isRunning = statusLower !in TERMINAL_STATUSES
    val isError = statusLower == "failed" || statusLower == "error" || statusLower == "cancelled"
    var expanded by remember(tool.toolCallId) { mutableStateOf(isRunning) }
    val context = LocalContext.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    LaunchedEffect(tool.textOutput.length, isRunning, expanded) {
        if (isRunning && expanded) {
            vScroll.animateScrollTo(vScroll.maxValue)
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunning) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
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
                when {
                    isRunning -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    isError -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tool.kind} · ${tool.status}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tool.diffPath != null) {
                    Surface(
                        onClick = onOpenDiff,
                        shape = M3ExpressiveShape.Full,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Diff",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (tool.textOutput.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("task_log", tool.textOutput))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy log",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = tool.textOutput.ifBlank {
                            if (isRunning) "Waiting for output…" else "(no text output)"
                        },
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 240.dp)
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}
