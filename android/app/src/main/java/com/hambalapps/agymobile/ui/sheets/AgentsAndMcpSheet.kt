package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpInitializeResult
import com.hambalapps.agymobile.data.acp.AcpSubagentInfo
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsAndMcpSheet(
    health: HealthResponse?,
    bridgeSettingsJson: String,
    initializeResult: AcpInitializeResult? = null,
    subagents: List<AcpSubagentInfo> = emptyList(),
    onOpenTerminal: () -> Unit,
    onRunAgentsTerminal: () -> Unit = onOpenTerminal,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Agents, MCP & Hooks", style = MaterialTheme.typography.titleLarge)

            ConnectedButtonGroup(
                options = listOf("MCP & Control", "Agents", "CLI Config"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

            when (selectedTab) {
                0 -> {
                    val priv = health?.privilegeLevel ?: "unknown"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "android-control-mcp",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Detected Privilege Backend: ${priv.uppercase()}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = when (priv) {
                                    "root" -> "Full root (su uid=0) active. Screenshots, UI tree, tap/swipe/type, app launching, and system shell tools are enabled."
                                    "shizuku" -> "Shizuku (rish uid=2000) active. Non-root ADB-level UI automation, screenshots, and app launching are enabled."
                                    "termux-api" -> "Termux:API active. Notifications and clipboard tools are enabled; UI automation requires su or Shizuku."
                                    else -> "Unprivileged shell mode (or remote desktop host). Grant su or start Shizuku in Termux for on-device control."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenTerminal,
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Open Terminal Drawer to test `su -c id` / `rish -c id` or run `agy acp --login`",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                1 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Connected ACP Agent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = initializeResult?.let { res ->
                                    if (res.agentVersion.isNotBlank()) "${res.agentName} (v${res.agentVersion})"
                                    else res.agentName
                                } ?: "Not connected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val capSummary = if (initializeResult == null) {
                                "Connect to agy-bridge to inspect live ACP capabilities."
                            } else {
                                buildList {
                                    if (initializeResult.supportsLoadSession) add("loadSession")
                                    if (initializeResult.supportsSessionList) add("session/list")
                                    if (initializeResult.supportsSessionResume) add("session/resume")
                                    if (initializeResult.supportsLogout) add("auth/logout")
                                    if (initializeResult.authMethods.isNotEmpty()) {
                                        add("${initializeResult.authMethods.size} auth methods")
                                    }
                                }.joinToString(" · ").ifBlank { "Basic ACP v${initializeResult.protocolVersion}" }
                            }
                            Text(
                                text = capSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Active Session Subagents",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (subagents.isNotEmpty()) {
                        subagents.forEach { subagent ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = subagent.name.ifBlank { subagent.id },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = subagent.status,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (subagent.role.isNotBlank()) {
                                        Text(
                                            text = subagent.role,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No subagents active in this session yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = onRunAgentsTerminal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp),
                                    shape = M3ExpressiveShape.Full
                                ) {
                                    Text("Inspect custom agents in Terminal (agy agents)")
                                }
                            }
                        }
                    }
                }
                else -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Text(
                            text = bridgeSettingsJson,
                            style = MonoCodeStyle,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
