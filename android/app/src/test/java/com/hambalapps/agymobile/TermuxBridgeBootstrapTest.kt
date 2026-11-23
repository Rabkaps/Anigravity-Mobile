package com.hambalapps.agymobile

import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBridgeBootstrapTest {

    @Test
    fun generateSecureToken_createsUnique64CharHexTokens() {
        val t1 = TermuxBridgeBootstrap.generateSecureToken()
        val t2 = TermuxBridgeBootstrap.generateSecureToken()

        assertEquals(64, t1.length)
        assertEquals(64, t2.length)
        assertTrue(t1.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(t1, t2)
    }

    @Test
    fun buildTermuxRunnerScript_embedsTokenPortAndDetachedNodeStart() {
        val script = TermuxBridgeBootstrap.buildTermuxRunnerScript(
            token = "abc123token",
            port = 4789
        )

        assertTrue(script.contains("npm install --omit=dev"))
        assertTrue(script.contains("abc123token"))
        assertTrue(script.contains("--port \"4789\""))
        assertTrue(script.contains("dist/server.js"))
    }

    @Test
    fun buildSelfExtractingTermuxCommand_producesCompactGzipBase64Pipeline() {
        val cmd = TermuxBridgeBootstrap.buildSelfExtractingTermuxCommand(
            files = mapOf("package.json" to "{\"name\":\"agy-bridge\"}".toByteArray()),
            token = "tok123",
            port = 4789
        )

        assertTrue(cmd.startsWith("printf '%s' '"))
        assertTrue(cmd.contains("base64 -d | gzip -dc |"))
    }
}
