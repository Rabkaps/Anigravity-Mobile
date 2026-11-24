package com.hambalapps.agymobile.ui.chat

enum class SlashCommandTarget {
    LOCAL_SHEET,
    LOCAL_ACTION,
    AGENT_PROMPT
}

enum class LocalSheetTrigger {
    MODE_MODEL,
    DIFF,
    ARTIFACT,
    AGENTS_MCP,
    SESSIONS,
    TERMINAL,
    SETTINGS
}

data class SlashCommandSpec(
    val command: String,
    val aliases: List<String> = emptyList(),
    val category: String,
    val description: String,
    val argHint: String? = null,
    val target: SlashCommandTarget = SlashCommandTarget.AGENT_PROMPT,
    val sheetTrigger: LocalSheetTrigger? = null
)

object SlashCommandCatalog {
    val builtIn: List<SlashCommandSpec> = listOf(
        SlashCommandSpec(
            command = "/plan",
            category = "Planning",
            description = "Enter planning mode before editing files",
            argHint = "[task]",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/boost",
            category = "Reasoning",
            description = "Run multi-agent deep reasoning loop",
            argHint = "<task>",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/goal",
            category = "Reasoning",
            description = "Run autonomously until the goal is verified complete",
            argHint = "<objective>",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/grill-me",
            category = "Planning",
            description = "Interview one question at a time before starting",
            argHint = "[task]",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/compact",
            category = "Conversations",
            description = "Summarize conversation into a compaction checkpoint",
            argHint = "[message]",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/fork",
            aliases = listOf("/branch"),
            category = "Conversations",
            description = "Branch current conversation into a parallel thread",
            target = SlashCommandTarget.LOCAL_ACTION
        ),
        SlashCommandSpec(
            command = "/resume",
            category = "Conversations",
            description = "Browse and resume saved conversations",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.SESSIONS
        ),
        SlashCommandSpec(
            command = "/clear",
            aliases = listOf("/new"),
            category = "Utilities",
            description = "Start a fresh session and clear chat view",
            target = SlashCommandTarget.LOCAL_ACTION
        ),
        SlashCommandSpec(
            command = "/diff",
            category = "Utilities",
            description = "Inspect workspace git diffs and file edits",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.DIFF
        ),
        SlashCommandSpec(
            command = "/artifact",
            category = "Tools & Tasks",
            description = "Open the Artifact Review sheet",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.ARTIFACT
        ),
        SlashCommandSpec(
            command = "/agents",
            category = "Tools & Tasks",
            description = "View active custom agents and background subagents",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.AGENTS_MCP
        ),
        SlashCommandSpec(
            command = "/mcp",
            category = "Tools & Tasks",
            description = "Manage MCP servers and Android Control privileges",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.AGENTS_MCP
        ),
        SlashCommandSpec(
            command = "/plugin",
            aliases = listOf("/plugins", "/marketplace", "/customizations"),
            category = "Tools & Tasks",
            description = "Browse Antigravity Plugin Marketplace and manage rules, skills & plugins",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.AGENTS_MCP
        ),
        SlashCommandSpec(
            command = "/hooks",
            category = "Tools & Tasks",
            description = "Inspect configured pre-flight/post-action hooks",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.AGENTS_MCP
        ),
        SlashCommandSpec(
            command = "/model",
            category = "Configurations",
            description = "Switch active reasoning model",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.MODE_MODEL
        ),
        SlashCommandSpec(
            command = "/effort",
            category = "Configurations",
            description = "Adjust reasoning effort (low / medium / high / max)",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.MODE_MODEL
        ),
        SlashCommandSpec(
            command = "/permissions",
            category = "Configurations",
            description = "Switch execution mode & tool permission policy",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.MODE_MODEL
        ),
        SlashCommandSpec(
            command = "/context",
            category = "Utilities",
            description = "Inspect token usage and model quotas",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.MODE_MODEL
        ),
        SlashCommandSpec(
            command = "/usage",
            aliases = listOf("/credits"),
            category = "Account",
            description = "View model quota usage and AI credits",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.MODE_MODEL
        ),
        SlashCommandSpec(
            command = "/config",
            aliases = listOf("/settings"),
            category = "Configurations",
            description = "Open connection profiles, bridge & theme settings",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.SETTINGS
        ),
        SlashCommandSpec(
            command = "/codesearch",
            aliases = listOf("/cs", "/search"),
            category = "Utilities",
            description = "Search code across the active workspace",
            argHint = "<query>",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/add-dir",
            category = "Utilities",
            description = "Add an extra directory path to workspace context",
            argHint = "<path>",
            target = SlashCommandTarget.LOCAL_ACTION
        ),
        SlashCommandSpec(
            command = "/btw",
            category = "Utilities",
            description = "Ask a quick side question without altering context",
            argHint = "<query>",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/learn",
            category = "Tools & Tasks",
            description = "Save session corrections as reusable rules or skills",
            argHint = "[instruction]",
            target = SlashCommandTarget.AGENT_PROMPT
        ),
        SlashCommandSpec(
            command = "/terminal",
            category = "Utilities",
            description = "Open interactive PTY terminal drawer (e.g. agy acp --login)",
            target = SlashCommandTarget.LOCAL_SHEET,
            sheetTrigger = LocalSheetTrigger.TERMINAL
        ),
        SlashCommandSpec(
            command = "/voice",
            category = "Utilities",
            description = "Start Android voice dictation into the composer",
            target = SlashCommandTarget.LOCAL_ACTION
        ),
        SlashCommandSpec(
            command = "/logout",
            category = "Account",
            description = "Sign out of Antigravity and clear cached credentials",
            target = SlashCommandTarget.LOCAL_ACTION
        )
    )

    fun filter(query: String, dynamicCommands: List<SlashCommandSpec> = emptyList()): List<SlashCommandSpec> {
        // Prioritize live commands advertised by `agy acp` (`available_commands_update`),
        // keeping local App UI sheets alongside them without overriding real agent commands.
        val merged = (dynamicCommands + builtIn).distinctBy { it.command }
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty() || normalized == "/") return merged

        val token = normalized.substringBefore(' ')
        return merged.filter { spec ->
            spec.command.lowercase().startsWith(token) ||
                spec.aliases.any { it.lowercase().startsWith(token) } ||
                spec.description.lowercase().contains(token.removePrefix("/"))
        }
    }
}
