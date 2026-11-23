package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpAuthMethod
import com.hambalapps.agymobile.data.acp.AcpInitializeResult
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSheet(
    initializeResult: AcpInitializeResult?,
    authInProgress: Boolean,
    authError: String?,
    onSelectAuthMethod: (AcpAuthMethod) -> Unit,
    onSubmitGeminiApiKey: (String) -> Unit,
    onOpenTerminalLogin: (command: String) -> Unit,
    onRetryHandshake: () -> Unit,
    onDismiss: () -> Unit
) {
    var showApiKeyInput by remember { mutableStateOf(false) }
    var apiKeyText by remember { mutableStateOf("") }

    val methods = initializeResult?.authMethods.orEmpty()

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "Sign in to Antigravity",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = initializeResult?.agentName ?: "Antigravity ACP Server",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (authInProgress) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveLoadingIndicator()
                    Text(
                        text = "Completing authentication…",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (!authError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = authError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (methods.isNotEmpty()) {
                methods.forEach { method ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3ExpressiveShape.Large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(method.name, style = MaterialTheme.typography.titleMedium)
                            if (method.description.isNotBlank()) {
                                Text(
                                    text = method.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    when {
                                        method.id == "gemini-api-key" -> showApiKeyInput = true
                                        method.type == "terminal" -> {
                                            val args = if (method.args.isNotEmpty()) {
                                                method.args.joinToString(" ")
                                            } else {
                                                "acp --login"
                                            }
                                            val envPrefix = method.env.entries.joinToString(" ") { (k, v) ->
                                                "$k=$v"
                                            }
                                            val cmd = if (envPrefix.isBlank()) "agy $args" else "$envPrefix agy $args"
                                            onOpenTerminalLogin(cmd)
                                        }
                                        else -> onSelectAuthMethod(method)
                                    }
                                },
                                enabled = !authInProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full
                            ) {
                                Icon(
                                    imageVector = if (method.type == "terminal") Icons.Rounded.Terminal
                                    else if (method.id == "gemini-api-key") Icons.Rounded.Key
                                    else Icons.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(method.name)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Sign in once using your Google account in the interactive login shell, or provide a Gemini API key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { onOpenTerminalLogin("agy acp --login") },
                    enabled = !authInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.Full
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Log in with Google (agy acp --login)")
                }
                FilledTonalButton(
                    onClick = { showApiKeyInput = !showApiKeyInput },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.Full
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Use Gemini API Key")
                }
            }

            if (showApiKeyInput) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            label = { Text("GEMINI_API_KEY") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = M3ExpressiveShape.Large
                        )
                        Button(
                            onClick = {
                                if (apiKeyText.isNotBlank()) {
                                    onSubmitGeminiApiKey(apiKeyText.trim())
                                }
                            },
                            enabled = apiKeyText.isNotBlank() && !authInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.Full
                        ) {
                            Text("Save API Key & Connect")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onRetryHandshake,
                enabled = !authInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = M3ExpressiveShape.Full
            ) {
                Text("Already signed in? Retry Session")
            }
        }
    }
}
