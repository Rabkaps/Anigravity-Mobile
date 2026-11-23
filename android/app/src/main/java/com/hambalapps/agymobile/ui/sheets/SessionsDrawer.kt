package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.SessionSummary
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.ui.chat.ActiveSheet
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape

@Composable
fun SessionsDrawerContent(
    workspaceCwd: String,
    profiles: List<ConnectionProfile>,
    activeProfileId: String?,
    sessions: List<SessionSummary>,
    onNewChat: () -> Unit,
    onForkChat: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onChangeWorkspace: (String) -> Unit,
    onLoadSession: (String) -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
    onOpenTerminal: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var cwdInput by remember(workspaceCwd) { mutableStateOf(workspaceCwd) }

    ModalDrawerSheet(
        modifier = Modifier.width(316.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Agy Mobile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onNewChat()
                        onCloseDrawer()
                    },
                    modifier = Modifier.weight(1f),
                    shape = M3ExpressiveShape.groupLeftShape
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
                FilledTonalButton(
                    onClick = {
                        onForkChat()
                        onCloseDrawer()
                    },
                    modifier = Modifier.weight(1f),
                    shape = M3ExpressiveShape.groupRightShape
                ) {
                    Icon(Icons.Rounded.CallSplit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Fork")
                }
            }

            // Workspace CWD
            OutlinedTextField(
                value = cwdInput,
                onValueChange = { cwdInput = it },
                label = { Text("Workspace (cwd)") },
                singleLine = true,
                trailingIcon = {
                    FilledTonalButton(
                        onClick = {
                            onChangeWorkspace(cwdInput)
                            onCloseDrawer()
                        },
                        shape = M3ExpressiveShape.Full,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Set")
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.Large
            )

            HorizontalDivider()

            Text("Host Profiles", style = MaterialTheme.typography.labelLarge)
            profiles.forEach { profile ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            profile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    badge = {
                        Text(
                            when {
                                profile.isStandalone -> "Standalone"
                                profile.isRemote -> "Remote"
                                else -> "Termux"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = profile.id == activeProfileId,
                    onClick = {
                        onSelectProfile(profile.id)
                        onCloseDrawer()
                    },
                    shape = M3ExpressiveShape.Full
                )
            }

            HorizontalDivider()

            Text("CLI Tools", style = MaterialTheme.typography.labelLarge)
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Difference, contentDescription = null) },
                label = { Text("Workspace Diff (/diff)") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.DIFF)
                }
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                label = { Text("Agents & MCP (/mcp)") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.AGENTS_MCP)
                }
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                label = { Text("Terminal & Login (/terminal)") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenTerminal()
                }
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Login, contentDescription = null) },
                label = { Text("Account / Sign In") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.AUTH)
                }
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text("Profiles & Theme (/config)") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.SETTINGS)
                }
            )

            if (sessions.isNotEmpty()) {
                HorizontalDivider()
                Text("Saved Sessions (/resume)", style = MaterialTheme.typography.labelLarge)
                sessions.take(12).forEach { session ->
                    Surface(
                        onClick = {
                            onLoadSession(session.id)
                            onCloseDrawer()
                        },
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = session.updatedAt.take(16),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
