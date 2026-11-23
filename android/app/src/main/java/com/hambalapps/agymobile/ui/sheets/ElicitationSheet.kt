package com.hambalapps.agymobile.ui.sheets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpElicitationRequest
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElicitationSheet(
    request: AcpElicitationRequest,
    onRespond: (action: String, content: Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val formValues = remember(request.rpcId) {
        mutableStateMapOf<String, String>().apply {
            request.fields.forEach { f ->
                put(f.key, f.defaultValue.ifBlank { f.enumOptions.firstOrNull().orEmpty() })
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onRespond("cancel", emptyMap()) },
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
            Text(
                text = if (request.mode == "url") "External Action Requested" else "Agent Input Requested",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyLarge
            )

            if (request.mode == "url" && !request.url.isNullOrBlank()) {
                val host = runCatching { Uri.parse(request.url).host }.getOrNull() ?: request.url
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Target host: $host", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = request.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(request.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        onRespond("accept", emptyMap())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = M3ExpressiveShape.Full
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                    Text("  Open in Browser & Continue")
                }
            } else {
                request.fields.forEach { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (field.required) "${field.title} *" else field.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (field.description.isNotBlank()) {
                            Text(
                                text = field.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (field.enumOptions.isNotEmpty() && field.enumOptions.size <= 4) {
                            val currentVal = formValues[field.key].orEmpty()
                            val selectedIdx = field.enumOptions.indexOf(currentVal).coerceAtLeast(0)
                            ConnectedButtonGroup(
                                options = field.enumOptions,
                                selectedIndex = selectedIdx,
                                onSelect = { idx -> formValues[field.key] = field.enumOptions[idx] },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = formValues[field.key].orEmpty(),
                                onValueChange = { formValues[field.key] = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = M3ExpressiveShape.Large
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRespond("decline", emptyMap()) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = M3ExpressiveShape.groupLeftShape
                    ) {
                        Text("Decline")
                    }
                    FilledTonalButton(
                        onClick = { onRespond("accept", formValues.toMap()) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = M3ExpressiveShape.groupRightShape
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
