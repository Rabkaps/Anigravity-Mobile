package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpInitializeResult
import com.hambalapps.agymobile.data.acp.AcpSubagentInfo
import com.hambalapps.agymobile.data.acp.HealthResponse
import com.hambalapps.agymobile.data.engine.InstalledPluginInfo
import com.hambalapps.agymobile.data.engine.MarketplaceCatalogItem
import com.hambalapps.agymobile.data.engine.SkillItemInfo
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsAndMcpSheet(
    health: HealthResponse?,
    bridgeSettingsJson: String,
    initializeResult: AcpInitializeResult? = null,
    subagents: List<AcpSubagentInfo> = emptyList(),
    onOpenTerminal: () -> Unit,
    onRunAgentsTerminal: () -> Unit = onOpenTerminal,
    onDismiss: () -> Unit,
    isStandalone: Boolean = true,
    standaloneToolchainInstalled: Boolean = true,
    marketplaceCatalog: List<MarketplaceCatalogItem> = emptyList(),
    installedPlugins: List<InstalledPluginInfo> = emptyList(),
    isLoadingPlugins: Boolean = false,
    pluginActionTarget: String? = null,
    pluginStatusMessage: String? = null,
    globalRulesText: String = "",
    workspaceRulesText: String = "",
    discoveredSkills: List<SkillItemInfo> = emptyList(),
    onRefreshPlugins: (forceCatalog: Boolean) -> Unit = {},
    onInstallPlugin: (String) -> Unit = {},
    onTogglePlugin: (name: String, enabled: Boolean) -> Unit = { _, _ -> },
    onUninstallPlugin: (name: String) -> Unit = {},
    onSaveRules: (isGlobal: Boolean, content: String) -> Unit = { _, _ -> },
    onSaveSkill: (name: String, content: String, isGlobal: Boolean) -> Unit = { _, _, _ -> },
    onUpdateCliSettingBoolean: (key: String, enabled: Boolean) -> Unit = { _, _ -> },
    onInstallToolchain: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var customSpecInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Customizations & Marketplace", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { onRefreshPlugins(true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh catalog")
                }
            }

            ConnectedButtonGroup(
                options = listOf("Marketplace", "Installed", "Rules & Skills", "MCP & CLI"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

            if (!pluginStatusMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = pluginStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            var globalDraft by remember(globalRulesText) { mutableStateOf(globalRulesText) }
            var wsDraft by remember(workspaceRulesText) { mutableStateOf(workspaceRulesText) }
            var newSkillName by remember { mutableStateOf("") }
            var newSkillBody by remember { mutableStateOf("") }

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Google & Partner plugins…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Full
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Google", "Partner", "Build with Google", "Google Workspace", "Developer Tools", "Productivity").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customSpecInput,
                            onValueChange = { customSpecInput = it },
                            placeholder = { Text("name@marketplace or GitHub URL…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = M3ExpressiveShape.Large
                        )
                        FilledTonalButton(
                            onClick = {
                                if (customSpecInput.isNotBlank()) {
                                    onInstallPlugin(customSpecInput.trim())
                                    customSpecInput = ""
                                }
                            },
                            enabled = customSpecInput.isNotBlank() && pluginActionTarget == null,
                            shape = M3ExpressiveShape.Full,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Install")
                        }
                    }

                    if (isLoadingPlugins && marketplaceCatalog.isEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveLoadingIndicator()
                            Text("Loading Marketplace catalog…", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    val installedSlugs = remember(installedPlugins) {
                        installedPlugins.flatMap { listOf(it.name.lowercase(), it.dirName.lowercase()) }.toSet()
                    }
                    val filtered = remember(marketplaceCatalog, searchQuery, selectedFilter) {
                        marketplaceCatalog.filter { item ->
                            val matchesFilter = when (selectedFilter) {
                                "All" -> true
                                "Google" -> item.publisher.equals("Google", ignoreCase = true)
                                "Partner" -> item.publisher.equals("Partner", ignoreCase = true)
                                "Productivity" -> item.shelf.contains("Productivity", ignoreCase = true)
                                else -> item.shelf.equals(selectedFilter, ignoreCase = true)
                            }
                            val q = searchQuery.trim().lowercase()
                            val matchesQuery = q.isEmpty() ||
                                item.displayName.lowercase().contains(q) ||
                                item.slug.lowercase().contains(q) ||
                                item.description.lowercase().contains(q) ||
                                item.shelf.lowercase().contains(q)
                            matchesFilter && matchesQuery
                        }
                    }

                    filtered.forEach { item ->
                        val isInstalled = item.slug.lowercase() in installedSlugs
                        val isInstallingThis = pluginActionTarget == item.installSpec
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.publisher} • ${item.shelf}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isInstalled) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    "Installed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { onInstallPlugin(item.installSpec) },
                                            enabled = pluginActionTarget == null,
                                            shape = M3ExpressiveShape.Full
                                        ) {
                                            if (isInstallingThis) {
                                                ExpressiveLoadingIndicator()
                                            } else {
                                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("Add")
                                            }
                                        }
                                    }
                                }
                                if (item.description.isNotBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Installed Plugins (${installedPlugins.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { onRefreshPlugins(false) }) {
                            Text("Refresh")
                        }
                    }

                    if (installedPlugins.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No plugins installed yet. Switch to the Marketplace tab to install Google & Partner plugins.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { selectedTab = 0 },
                                    shape = M3ExpressiveShape.Full
                                ) {
                                    Text("Browse Marketplace")
                                }
                            }
                        }
                    } else {
                        installedPlugins.forEach { plugin ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (plugin.version.isNotBlank()) "${plugin.name} (${plugin.version})" else plugin.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            if (plugin.source.isNotBlank()) {
                                                Text(
                                                    text = plugin.source,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = plugin.enabled,
                                            enabled = pluginActionTarget == null,
                                            onCheckedChange = { checked -> onTogglePlugin(plugin.name, checked) }
                                        )
                                        IconButton(
                                            onClick = { onUninstallPlugin(plugin.name) },
                                            enabled = pluginActionTarget == null
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Uninstall plugin")
                                        }
                                    }
                                    val comps = buildList {
                                        if (plugin.skills.isNotEmpty()) add("${plugin.skills.size} skills")
                                        if (plugin.mcpServers.isNotEmpty()) add("${plugin.mcpServers.size} MCP")
                                        if (plugin.agents.isNotEmpty()) add("${plugin.agents.size} agents")
                                        if (plugin.rulesCount > 0) add("${plugin.rulesCount} rules")
                                        if (plugin.hasHooks) add("hooks")
                                    }
                                    if (comps.isNotEmpty()) {
                                        Text(
                                            text = comps.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Connected ACP Agent & Subagents",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                text = initializeResult?.let { res ->
                                    if (res.agentVersion.isNotBlank()) "${res.agentName} (v${res.agentVersion})"
                                    else res.agentName
                                } ?: "Not connected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val capSummary = if (initializeResult == null) {
                                "Connect to agy to inspect live ACP capabilities."
                            } else {
                                buildList {
                                    if (initializeResult.supportsLoadSession) add("loadSession")
                                    if (initializeResult.supportsSessionList) add("session/list")
                                    if (initializeResult.supportsSessionResume) add("session/resume")
                                    if (initializeResult.supportsLogout) add("auth/logout")
                                }.joinToString(" · ").ifBlank { "Basic ACP v${initializeResult.protocolVersion}" }
                            }
                            Text(
                                text = capSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (subagents.isNotEmpty()) {
                                subagents.forEach { sub ->
                                    Text(
                                        text = "• ${sub.name.ifBlank { sub.id }} (${sub.status})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onRunAgentsTerminal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 44.dp),
                                    shape = M3ExpressiveShape.Full
                                ) {
                                    Text("Inspect custom agents in Terminal (agy agents)")
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Global Rules (~/.gemini/config/GEMINI.md)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = globalDraft,
                                onValueChange = { globalDraft = it },
                                placeholder = { Text("Always-on instructions across all projects…") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large
                            )
                            Button(
                                onClick = { onSaveRules(true, globalDraft) },
                                shape = M3ExpressiveShape.Full,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Global Rules")
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Workspace Rules (GEMINI.md)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = wsDraft,
                                onValueChange = { wsDraft = it },
                                placeholder = { Text("Project-specific coding style or constraints…") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large
                            )
                            Button(
                                onClick = { onSaveRules(false, wsDraft) },
                                shape = M3ExpressiveShape.Full,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Workspace Rules")
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Custom Skills (${discoveredSkills.size})", style = MaterialTheme.typography.titleMedium)
                            discoveredSkills.forEach { sk ->
                                Text(
                                    text = "• /${sk.name} (${sk.scope})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedTextField(
                                value = newSkillName,
                                onValueChange = { newSkillName = it },
                                label = { Text("New Skill Name (e.g. code-review)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large
                            )
                            OutlinedTextField(
                                value = newSkillBody,
                                onValueChange = { newSkillBody = it },
                                label = { Text("SKILL.md Instructions") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newSkillName.isNotBlank() && newSkillBody.isNotBlank()) {
                                        onSaveSkill(newSkillName, newSkillBody, true)
                                        newSkillName = ""
                                        newSkillBody = ""
                                    }
                                },
                                enabled = newSkillName.isNotBlank() && newSkillBody.isNotBlank(),
                                shape = M3ExpressiveShape.Full,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Create Global Skill")
                            }
                        }
                    }
                }

                else -> {
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
                                    else -> "Unprivileged shell mode (or remote desktop host). Grant su or start Shizuku for on-device control."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isStandalone && !standaloneToolchainInstalled) {
                                FilledTonalButton(
                                    onClick = {
                                        onDismiss()
                                        onInstallToolchain()
                                    },
                                    shape = M3ExpressiveShape.Full,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Install Git & Node/NPM to Enable android-control-mcp")
                                }
                            }
                        }
                    }

                    if (isStandalone) {
                        val settingsObj = remember(bridgeSettingsJson) {
                            runCatching { JSONObject(bridgeSettingsJson) }.getOrDefault(JSONObject())
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("CLI Settings (settings.json)", style = MaterialTheme.typography.titleMedium)
                                listOf(
                                    Triple("allowNonWorkspaceAccess", "Allow Non-Workspace Access", "Read/edit files outside workspace"),
                                    Triple("useG1Credits", "Use AI Credits Fallback", "Use G1 credits when quota is depleted"),
                                    Triple("notifications", "CLI Notifications", "Emit notifications on approval/completion")
                                ).forEach { (key, title, desc) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = settingsObj.optBoolean(key, false),
                                            onCheckedChange = { onUpdateCliSettingBoolean(key, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

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

                    Surface(
                        onClick = onOpenTerminal,
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Open Terminal Drawer (`su -c id` / `rish -c id` / `agy`)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
