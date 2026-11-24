package com.hambalapps.agymobile.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpAuthMethod
import com.hambalapps.agymobile.data.acp.AcpInitializeResult
import com.hambalapps.agymobile.data.engine.GoogleLoginPhase
import com.hambalapps.agymobile.data.engine.GoogleLoginState
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle

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
    onDismiss: () -> Unit,
    isStandalone: Boolean = true,
    standaloneToolchainInstalled: Boolean = false,
    googleLoginState: GoogleLoginState = GoogleLoginState(),
    onStartGuidedGoogleLogin: () -> Unit = {},
    onSubmitGoogleAuthCode: (String) -> Unit = {},
    onCancelGuidedGoogleLogin: () -> Unit = {},
    onOpenBrowserUrl: (String) -> Unit = {},
    onInstallToolchain: () -> Unit = {}
) {
    val context = LocalContext.current
    var showApiKeyInput by remember { mutableStateOf(false) }
    var apiKeyText by remember { mutableStateOf("") }
    var authCodeText by remember { mutableStateOf("") }
    var showCliLog by remember { mutableStateOf(false) }

    val methods = initializeResult?.authMethods.orEmpty()
    val guidedBusy = googleLoginState.phase == GoogleLoginPhase.STARTING ||
        googleLoginState.phase == GoogleLoginPhase.SUBMITTING_CODE

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

            if (authInProgress || guidedBusy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveLoadingIndicator()
                    Text(
                        text = when (googleLoginState.phase) {
                            GoogleLoginPhase.STARTING -> "Generating Google sign-in link…"
                            GoogleLoginPhase.SUBMITTING_CODE -> "Verifying authorization code…"
                            else -> "Completing authentication…"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            val combinedError = googleLoginState.errorMessage ?: authError
            if (!combinedError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = combinedError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isStandalone) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Sign in with Google (Guided)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Opens Google Sign-In in your browser without timing out. After approving, copy the code shown in the browser and paste it below. Works even before Git or NPM is installed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        when (googleLoginState.phase) {
                            GoogleLoginPhase.IDLE, GoogleLoginPhase.ERROR, GoogleLoginPhase.SUCCESS -> {
                                Button(
                                    onClick = onStartGuidedGoogleLogin,
                                    enabled = !authInProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp),
                                    shape = M3ExpressiveShape.Full
                                ) {
                                    Icon(
                                        Icons.Rounded.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        if (googleLoginState.phase == GoogleLoginPhase.ERROR) "Retry Google Sign-In"
                                        else "Start Google Sign-In"
                                    )
                                }
                            }
                            GoogleLoginPhase.STARTING,
                            GoogleLoginPhase.AWAITING_CODE,
                            GoogleLoginPhase.SUBMITTING_CODE -> {
                                val url = googleLoginState.authUrl
                                if (url != null) {
                                    Text(
                                        text = "Step 1: Complete sign-in in your browser",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onOpenBrowserUrl(url) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp),
                                            shape = M3ExpressiveShape.Full
                                        ) {
                                            Icon(
                                                Icons.Rounded.OpenInBrowser,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text("Open Google Sign-In")
                                        }
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(ClipboardManager::class.java)
                                                cm?.setPrimaryClip(ClipData.newPlainText("Google Sign-In URL", url))
                                                Toast.makeText(context, "Sign-in URL copied", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy sign-in URL")
                                        }
                                    }
                                }

                                Text(
                                    text = "Step 2: Paste the code from your browser",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = authCodeText,
                                        onValueChange = { authCodeText = it },
                                        label = { Text("Authorization code") },
                                        placeholder = { Text("Paste code here…") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = M3ExpressiveShape.Large
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(ClipboardManager::class.java)
                                            val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                            if (clip.isNotBlank()) {
                                                authCodeText = clip
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste code")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            authCodeText = ""
                                            onCancelGuidedGoogleLogin()
                                        },
                                        enabled = !guidedBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp),
                                        shape = M3ExpressiveShape.groupLeftShape
                                    ) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = { onSubmitGoogleAuthCode(authCodeText) },
                                        enabled = authCodeText.isNotBlank() && !guidedBusy,
                                        modifier = Modifier
                                            .weight(2f)
                                            .defaultMinSize(minHeight = 48.dp),
                                        shape = M3ExpressiveShape.groupRightShape
                                    ) {
                                        Text("Verify & Sign In")
                                    }
                                }
                            }
                        }

                        if (googleLoginState.logOutput.isNotBlank()) {
                            TextButton(onClick = { showCliLog = !showCliLog }) {
                                Icon(
                                    imageVector = if (showCliLog) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(if (showCliLog) "Hide CLI Output" else "View CLI Output")
                            }
                            AnimatedVisibility(visible = showCliLog) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = M3ExpressiveShape.Medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Text(
                                        text = googleLoginState.logOutput.takeLast(1500),
                                        style = MonoCodeStyle,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (methods.isNotEmpty()) {
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
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { showApiKeyInput = !showApiKeyInput },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.groupLeftShape
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Gemini API Key")
                }
                OutlinedButton(
                    onClick = {
                        onOpenTerminalLogin("SSH_CONNECTION='127.0.0.1 22 127.0.0.1 22' agy acp --login")
                    },
                    enabled = !authInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.groupRightShape
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Shell Login")
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

            if (isStandalone && !standaloneToolchainInstalled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Optional: Install Git & Node/NPM anytime to unlock repo tools & android-control-mcp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            onDismiss()
                            onInstallToolchain()
                        }) {
                            Text("Install")
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
