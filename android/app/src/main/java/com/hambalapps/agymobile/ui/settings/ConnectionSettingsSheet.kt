package com.hambalapps.agymobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.local.AppPreferences
import com.hambalapps.agymobile.data.local.ConnectionProfile
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsSheet(
    preferences: AppPreferences?,
    testResult: String?,
    isBootstrapping: Boolean,
    bootstrapStatus: String?,
    standaloneInstalled: Boolean = false,
    standaloneToolchainInstalled: Boolean = false,
    standaloneProgress: Float? = null,
    detectedAbi: String = "",
    onStartStandaloneEngine: (includeToolchain: Boolean) -> Unit = {},
    onUpdateStandaloneAgy: () -> Unit = {},
    onInstallToolchain: () -> Unit = {},
    onAutoSetupBridge: (useRootSu: Boolean) -> Unit,
    onSaveProfile: (ConnectionProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onTestConnection: (ConnectionProfile) -> Unit,
    onToggleAmoled: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val active = preferences?.activeProfile
    var editingId by remember(active?.id) { mutableStateOf(active?.id ?: "local-standalone") }
    var name by remember(active?.id) { mutableStateOf(active?.name ?: "Standalone Engine (Built-in)") }
    var host by remember(active?.id) { mutableStateOf(active?.host ?: "127.0.0.1") }
    var portText by remember(active?.id) { mutableStateOf((active?.port ?: 4789).toString()) }
    var token by remember(active?.id) { mutableStateOf(active?.token ?: "") }
    var defaultCwd by remember(active?.id) {
        mutableStateOf(active?.defaultCwd ?: "/data/data/com.termux/files/home")
    }
    var useTls by remember(active?.id) { mutableStateOf(active?.useTls ?: false) }
    var isRemote by remember(active?.id) { mutableStateOf(active?.isRemote ?: false) }
    var isStandalone by remember(active?.id) { mutableStateOf(active?.isStandalone ?: false) }

    fun currentDraft(): ConnectionProfile = ConnectionProfile(
        id = editingId,
        name = name.ifBlank { "Bridge Host" },
        host = host.ifBlank { "127.0.0.1" },
        port = portText.toIntOrNull() ?: 4789,
        token = token.trim(),
        useTls = useTls,
        isRemote = isRemote,
        isStandalone = isStandalone,
        defaultCwd = defaultCwd.ifBlank { "/data/data/com.termux/files/home" }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Connections & Theme (/config)", style = MaterialTheme.typography.titleLarge)

            // 1-Tap Standalone + Termux Setup
            Surface(
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val abiTag = if (detectedAbi.isNotBlank()) " • $detectedAbi" else ""
                    Text(
                        text = "Built-In Standalone Engine$abiTag",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Runs official agy directly inside Agy Mobile (no Termux required), with optional portable bash/git/node/npm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBootstrapping) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveLoadingIndicator()
                                Text(
                                    text = bootstrapStatus ?: "Bootstrapping…",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            standaloneProgress?.let { prog ->
                                LinearProgressIndicator(
                                    progress = { prog.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    } else if (bootstrapStatus != null) {
                        Text(
                            text = bootstrapStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { onStartStandaloneEngine(false) },
                            enabled = !isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupLeftShape
                        ) {
                            Text(if (standaloneInstalled) "Start Standalone" else "Install Standalone")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (standaloneInstalled) onInstallToolchain()
                                else onStartStandaloneEngine(true)
                            },
                            enabled = !isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupRightShape
                        ) {
                            Text(if (standaloneToolchainInstalled) "Reinstall Git/Node" else "+ Git & Node/NPM")
                        }
                    }
                    if (standaloneInstalled) {
                        OutlinedButton(
                            onClick = onUpdateStandaloneAgy,
                            enabled = !isBootstrapping,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp),
                            shape = M3ExpressiveShape.Full
                        ) {
                            Text("Check & Update agy CLI")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAutoSetupBridge(false) },
                            enabled = !isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupLeftShape
                        ) {
                            Text("Start via Termux")
                        }
                        OutlinedButton(
                            onClick = { onAutoSetupBridge(true) },
                            enabled = !isBootstrapping,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.groupRightShape
                        ) {
                            Text("Zero-Touch (su)")
                        }
                    }
                }
            }

            // Theme Controls
            Surface(
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val modeKeys = listOf("system", "light", "dark", "solar")
                    val modeLabels = listOf("System", "Light", "Dark", "Dynamic")
                    val currentMode = preferences?.themeMode ?: "system"
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    ConnectedButtonGroup(
                        options = modeLabels,
                        selectedIndex = modeKeys.indexOf(currentMode).coerceAtLeast(0),
                        onSelect = { idx -> onSelectThemeMode(modeKeys[idx]) }
                    )
                    if (currentMode == "solar") {
                        Text(
                            text = "Adapts colors from warm morning light to deep night using your location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic Color (Monet)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Extract Expressive palette from wallpaper (Android 12+)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences?.dynamicColor ?: true,
                            onCheckedChange = onToggleDynamicColor
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AMOLED Pure-Black Dark", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "True black OLED surfaces in dark mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences?.amoledDark ?: false,
                            onCheckedChange = onToggleAmoled
                        )
                    }
                }
            }

            // Saved profiles strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bridge Profiles", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = {
                        editingId = UUID.randomUUID().toString()
                        name = "Remote PC"
                        host = "192.168.1."
                        portText = "4789"
                        token = ""
                        useTls = false
                        isRemote = true
                        isStandalone = false
                        defaultCwd = "~"
                    },
                    shape = M3ExpressiveShape.Full
                ) {
                    Text("+ New Remote")
                }
            }

            preferences?.profiles?.forEach { profile ->
                Surface(
                    onClick = {
                        onSelectProfile(profile.id)
                        editingId = profile.id
                        name = profile.name
                        host = profile.host
                        portText = profile.port.toString()
                        token = profile.token
                        useTls = profile.useTls
                        isRemote = profile.isRemote
                        isStandalone = profile.isStandalone
                        defaultCwd = profile.defaultCwd
                    },
                    shape = M3ExpressiveShape.Large,
                    color = if (profile.id == active?.id) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            val modeLabel = when {
                                profile.isStandalone -> "Built-in Standalone"
                                profile.isRemote -> "${profile.httpBaseUrl()} • Remote"
                                else -> "${profile.httpBaseUrl()} • Termux"
                            }
                            Text(
                                modeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (preferences.profiles.size > 1) {
                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete profile")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.Large
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = M3ExpressiveShape.Large
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = M3ExpressiveShape.Large
                )
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Bridge Bearer Token") },
                placeholder = { Text("Printed by agy-bridge on startup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.Large
            )

            OutlinedTextField(
                value = defaultCwd,
                onValueChange = { defaultCwd = it },
                label = { Text("Default Workspace Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = M3ExpressiveShape.Large
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remote Host Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isRemote, onCheckedChange = { isRemote = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use TLS (https / wss)", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = useTls, onCheckedChange = { useTls = it })
            }

            if (testResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onTestConnection(currentDraft()) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.groupLeftShape
                ) {
                    Text("Test Health")
                }
                Button(
                    onClick = {
                        onSaveProfile(currentDraft())
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.groupRightShape
                ) {
                    Text("Save & Connect")
                }
            }
        }
    }
}
