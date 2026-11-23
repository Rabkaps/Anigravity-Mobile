package com.hambalapps.agymobile.ui.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.DiffFileEntry
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import com.hambalapps.agymobile.ui.theme.MonoCodeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerSheet(
    files: List<DiffFileEntry>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = M3ExpressiveShape.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Workspace Diff (/diff)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${files.size} changed file(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh diff")
                }
            }

            if (files.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "No uncommitted git changes in the active workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(files, key = { it.path }) { entry ->
                        DiffFileCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFileCard(entry: DiffFileEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = M3ExpressiveShape.Full,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = entry.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (entry.diff.isNotBlank()) {
                val annotated = buildAnnotatedString {
                    entry.diff.lines().forEach { line ->
                        val color = when {
                            line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF4ADE80)
                            line.startsWith("-") && !line.startsWith("---") -> Color(0xFFF87171)
                            line.startsWith("@@") -> Color(0xFF60A5FA)
                            else -> Color.Unspecified
                        }
                        if (color != Color.Unspecified) {
                            withStyle(SpanStyle(color = color)) { appendLine(line) }
                        } else {
                            appendLine(line)
                        }
                    }
                }
                Text(
                    text = annotated,
                    style = MonoCodeStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}
