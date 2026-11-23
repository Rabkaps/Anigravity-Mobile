package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.PtyWebSocketClient
import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSheet(
    ptyClient: PtyWebSocketClient,
    pendingCommand: String? = null,
    openedForAuth: Boolean = false,
    onConsumedPendingCommand: () -> Unit = {},
    onDoneAuthReconnect: () -> Unit = {},
    onOpenUrl: (url: String, force: Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val output by ptyClient.output.collectAsState()
    val connected by ptyClient.connected.collectAsState()
    val detectedUrl by ptyClient.detectedUrl.collectAsState()
    var cmdInput by remember { mutableStateOf("") }
    var autoOpenedUrl by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(connected, pendingCommand) {
        if (connected && !pendingCommand.isNullOrBlank()) {
            delay(350)
            ptyClient.sendCommand(pendingCommand)
            onConsumedPendingCommand()
        }
    }

    LaunchedEffect(detectedUrl, openedForAuth) {
        val url = detectedUrl
        if (url != null && url != autoOpenedUrl && (openedForAuth || PtyWebSocketClient.isAuthLikeUrl(url))) {
            autoOpenedUrl = url
            onOpenUrl(url, false)
        }
    }

    LaunchedEffect(output.length) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (openedForAuth) "Sign in to Antigravity" else "Host PTY Terminal",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (connected) "Connected (interactive shell)" else "Disconnected",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                if (openedForAuth) {
                    Button(
                        onClick = onDoneAuthReconnect,
                        shape = M3ExpressiveShape.Full
                    ) {
                        Text("Done — Reconnect")
                    }
                }
                IconButton(onClick = { ptyClient.clearOutput() }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear terminal")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Install agy CLI" to TermuxBridgeBootstrap.AGY_TERMUX_INSTALL_CMD,
                    "agy acp --login" to "agy acp --login",
                    "agy --version" to "agy --version",
                    "su -c id" to "su -c id",
                    "rish -c id" to "rish -c id",
                    "Ctrl+C" to "\u0003"
                ).forEach { (label, payload) ->
                    AssistChip(
                        onClick = {
                            if (payload == "\u0003") ptyClient.sendRaw(payload)
                            else ptyClient.sendCommand(payload)
                        },
                        label = { Text(label) }
                    )
                }
            }

            if (detectedUrl != null) {
                FilledTonalButton(
                    onClick = { detectedUrl?.let { onOpenUrl(it, true) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Full
                ) {
                    Icon(
                        Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (PtyWebSocketClient.isAuthLikeUrl(detectedUrl.orEmpty())) {
                            "Open Login URL in Browser"
                        } else {
                            "Open Detected URL in Browser"
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Text(
                    text = output.ifBlank { "Connecting to host shell…\n" },
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    placeholder = { Text("Enter shell command or paste auth code…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = M3ExpressiveShape.Full
                )
                IconButton(
                    onClick = {
                        if (cmdInput.isNotBlank()) {
                            ptyClient.sendCommand(cmdInput)
                            cmdInput = ""
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Run command")
                }
            }
        }
    }
}
