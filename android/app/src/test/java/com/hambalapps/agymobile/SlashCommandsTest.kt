package com.hambalapps.agymobile

import com.hambalapps.agymobile.ui.chat.SlashCommandCatalog
import com.hambalapps.agymobile.ui.chat.SlashCommandTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {

    @Test
    fun filter_matchesPrimaryCommandAndAliases() {
        val branchMatches = SlashCommandCatalog.filter("/branch")
        assertTrue(branchMatches.any { it.command == "/fork" })

        val csMatches = SlashCommandCatalog.filter("/cs")
        assertTrue(csMatches.any { it.command == "/codesearch" })
    }

    @Test
    fun filter_returnsAllCommandsForBareSlash() {
        val all = SlashCommandCatalog.filter("/")
        assertEquals(SlashCommandCatalog.builtIn.size, all.size)
    }

    @Test
    fun planAndBoostRouteToAgentPrompt() {
        val plan = SlashCommandCatalog.builtIn.first { it.command == "/plan" }
        val boost = SlashCommandCatalog.builtIn.first { it.command == "/boost" }
        assertEquals(SlashCommandTarget.AGENT_PROMPT, plan.target)
        assertEquals(SlashCommandTarget.AGENT_PROMPT, boost.target)
    }
}
