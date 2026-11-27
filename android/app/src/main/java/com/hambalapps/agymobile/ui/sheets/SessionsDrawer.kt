package com.hambalapps.agymobile.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.SessionSummary
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.data.local.LocalProject
import com.hambalapps.agymobile.ui.chat.ActiveSheet
import com.hambalapps.agymobile.ui.chat.detectTextDirection
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
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
    onCloseDrawer: () -> Unit,
    isSignedIn: Boolean = false,
    onLogout: () -> Unit = {},
    projects: List<LocalProject> = emptyList(),
    activeProjectId: String? = null,
    onSelectProject: (String) -> Unit = {},
    onSaveProject: (name: String, rootDir: String, extraDirs: List<String>, existingId: String?) -> Unit = { _, _, _, _ -> },
    onDeleteProject: (String) -> Unit = {}
) {
    var workspaceExpanded by remember { mutableStateOf(false) }
    var cwdInput by remember(workspaceCwd) { mutableStateOf(workspaceCwd) }
    var showNewProjectForm by remember { mutableStateOf(false) }
    var newProjName by remember { mutableStateOf("") }
    var newProjRoot by remember(workspaceCwd) { mutableStateOf(workspaceCwd) }
    var newProjExtras by remember { mutableStateOf("") }

    val activeProject = projects.firstOrNull { it.id == activeProjectId }
    val activeWorkspaceTitle = activeProject?.name
        ?: workspaceCwd.trimEnd('/').substringAfterLast('/').ifBlank { "Workspace" }

    ModalDrawerSheet(
        modifier = Modifier.width(312.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = M3ExpressiveShape.Medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Antigravity",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close drawer")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Primary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onNewChat()
                        onCloseDrawer()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.Full
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New chat")
                }
                FilledTonalIconButton(
                    onClick = {
                        onForkChat()
                        onCloseDrawer()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.CallSplit, contentDescription = "Fork chat")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Collapsible Active Workspace Card
            Surface(
                onClick = { workspaceExpanded = !workspaceExpanded },
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.LargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WORKSPACE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeWorkspaceTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (workspaceExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (workspaceExpanded) "Collapse workspace" else "Switch workspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = workspaceExpanded,
                enter = fadeIn() + expandVertically(M3ExpressiveMotion.adaptiveSpatialFast()),
                exit = fadeOut() + shrinkVertically(M3ExpressiveMotion.adaptiveSpatialFast())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cwdInput,
                        onValueChange = { cwdInput = it },
                        label = { Text("Folder path") },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Projects", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { showNewProjectForm = !showNewProjectForm }) {
                            Text(if (showNewProjectForm) "Cancel" else "+ New")
                        }
                    }

                    if (showNewProjectForm) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newProjName,
                                    onValueChange = { newProjName = it },
                                    label = { Text("Project Name") },
                                    placeholder = { Text("e.g. MyApp") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = M3ExpressiveShape.Large
                                )
                                OutlinedTextField(
                                    value = newProjRoot,
                                    onValueChange = { newProjRoot = it },
                                    label = { Text("Primary Folder") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = M3ExpressiveShape.Large
                                )
                                OutlinedTextField(
                                    value = newProjExtras,
                                    onValueChange = { newProjExtras = it },
                                    label = { Text("Extra Folders (--add-dir)") },
                                    placeholder = { Text("Optional, comma-separated") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = M3ExpressiveShape.Large
                                )
                                Button(
                                    onClick = {
                                        val extras = newProjExtras.split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        onSaveProject(newProjName, newProjRoot, extras, null)
                                        newProjName = ""
                                        newProjExtras = ""
                                        showNewProjectForm = false
                                        onCloseDrawer()
                                    },
                                    enabled = newProjName.isNotBlank() && newProjRoot.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = M3ExpressiveShape.Full
                                ) {
                                    Text("Create & Open")
                                }
                            }
                        }
                    }

                    projects.forEach { project ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                            label = {
                                Text(
                                    project.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            badge = {
                                if (projects.size > 1) {
                                    IconButton(onClick = { onDeleteProject(project.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete project")
                                    }
                                }
                            },
                            selected = project.id == activeProjectId,
                            onClick = {
                                onSelectProject(project.id)
                                onCloseDrawer()
                            },
                            shape = M3ExpressiveShape.Full
                        )
                    }

                    if (profiles.isNotEmpty()) {
                        Text("Hosts", style = MaterialTheme.typography.labelLarge)
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
                                            profile.isStandalone -> "Local"
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
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Scrollable Recent Chats
            Text(
                text = "RECENT CHATS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sessions.isEmpty()) {
                    Text(
                        text = "No saved chats yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                } else {
                    sessions.take(15).forEach { session ->
                        Surface(
                            onClick = {
                                onLoadSession(session.id)
                                onCloseDrawer()
                            },
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = detectTextDirection(session.title)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = session.updatedAt.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Tidy Footer
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                label = { Text("Terminal") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenTerminal()
                },
                shape = M3ExpressiveShape.Full
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                label = { Text("Plugins & MCP") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.AGENTS_MCP)
                },
                shape = M3ExpressiveShape.Full
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onOpenSheet(ActiveSheet.SETTINGS)
                },
                shape = M3ExpressiveShape.Full
            )
            if (isSignedIn) {
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    label = {
                        Text(
                            "Sign out",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onLogout()
                    },
                    shape = M3ExpressiveShape.Full
                )
            } else {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Rounded.Login, contentDescription = null) },
                    label = { Text("Sign in") },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onOpenSheet(ActiveSheet.AUTH)
                    },
                    shape = M3ExpressiveShape.Full
                )
            }
        }
    }
}
