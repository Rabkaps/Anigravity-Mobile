package com.hambalapps.agymobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpPermissionRequest
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestSheet(
    request: AcpPermissionRequest,
    onRespond: (optionId: String?) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { onRespond(null) },
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "Tool Permission Required",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Category: ${request.kind}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (request.rawInputPreview.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Text(
                        text = request.rawInputPreview,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                request.options.forEach { option ->
                    val isReject = option.kind.startsWith("reject")
                    val isAlways = option.kind == "allow_always"

                    when {
                        isReject -> {
                            OutlinedButton(
                                onClick = { onRespond(option.optionId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(option.name)
                            }
                        }
                        isAlways -> {
                            FilledTonalButton(
                                onClick = { onRespond(option.optionId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full
                            ) {
                                Text(option.name)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onRespond(option.optionId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = M3ExpressiveShape.Full
                            ) {
                                Text(option.name)
                            }
                        }
                    }
                }
            }
        }
    }
}
