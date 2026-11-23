package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.chat.ArtifactUi
import com.hambalapps.agymobile.ui.chat.MarkdownText
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactReviewSheet(
    artifacts: List<ArtifactUi>,
    selectedArtifactId: String?,
    onDecision: (artifactId: String, approved: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val active = artifacts.firstOrNull { it.id == selectedArtifactId } ?: artifacts.lastOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 660.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = active?.title ?: "Artifact Review (/artifact)",
                style = MaterialTheme.typography.titleLarge
            )

            if (active == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "No plan or walkthrough artifacts captured in this session yet. Try running /plan first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    MarkdownText(
                        markdown = active.markdown,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDecision(active.id, false) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = M3ExpressiveShape.groupLeftShape
                    ) {
                        Text("Request Changes")
                    }
                    Button(
                        onClick = { onDecision(active.id, true) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = M3ExpressiveShape.groupRightShape
                    ) {
                        Text("Approve & Proceed")
                    }
                }
            }
        }
    }
}
