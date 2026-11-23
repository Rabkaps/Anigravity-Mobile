package com.hambalapps.agymobile.ui.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.data.acp.AcpConfigOption
import com.hambalapps.agymobile.data.acp.ModelQuotaInfo
import com.hambalapps.agymobile.data.acp.QuotaBucketInfo
import com.hambalapps.agymobile.data.acp.QuotaSummaryResponse
import com.hambalapps.agymobile.ui.components.ConnectedButtonGroup
import com.hambalapps.agymobile.ui.components.ExpressiveLoadingIndicator
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
import com.hambalapps.agymobile.ui.theme.M3ExpressiveShape
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeAndModelSheet(
    configOptions: List<AcpConfigOption>,
    usedTokens: Int,
    maxTokens: Int,
    quotaSummary: QuotaSummaryResponse? = null,
    isLoadingQuota: Boolean = false,
    quotaLookup: (value: String, name: String) -> ModelQuotaInfo? = { _, _ -> null },
    onRefreshQuota: () -> Unit = {},
    onSetConfigOption: (configId: String, value: String) -> Unit,
    onOpenAuth: () -> Unit,
    onDismiss: () -> Unit
) {
    val contextRatio = if (maxTokens > 0) (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f) else 0f
    val animatedContext by animateFloatAsState(
        targetValue = contextRatio,
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "contextRatio"
    )

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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Session Options, Model & Quota",
                style = MaterialTheme.typography.titleLarge
            )

            // Context Window Meter (/context)
            Surface(
                shape = M3ExpressiveShape.Large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Context Window", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${(contextRatio * 100).roundToInt()}% (${usedTokens / 1000}k / ${maxTokens / 1000}k)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animatedContext },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            // Models & Quota Summary Card
            QuotaSummaryCard(
                quotaSummary = quotaSummary,
                isLoading = isLoadingQuota,
                onRefresh = onRefreshQuota
            )

            if (configOptions.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3ExpressiveShape.Large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No live session options loaded yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Models, execution modes, and thinking levels are populated directly from your authenticated agy acp session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onOpenAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = M3ExpressiveShape.Full
                        ) {
                            Text("Sign in / Check Auth")
                        }
                    }
                }
            } else {
                configOptions.forEach { option ->
                    DynamicConfigOptionBlock(
                        option = option,
                        quotaLookup = quotaLookup,
                        onSelect = { value -> onSetConfigOption(option.id, value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaSummaryCard(
    quotaSummary: QuotaSummaryResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = M3ExpressiveShape.Large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Models & Quota", style = MaterialTheme.typography.titleMedium)
                    val emailText = quotaSummary?.email
                    if (!emailText.isNullOrBlank()) {
                        Text(
                            text = emailText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isLoading) {
                    ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh quota"
                        )
                    }
                }
            }

            val buckets = quotaSummary?.buckets.orEmpty()
            when {
                buckets.isNotEmpty() -> {
                    buckets.forEach { bucket ->
                        QuotaBucketRow(bucket = bucket)
                    }
                }
                !quotaSummary?.error.isNullOrBlank() -> {
                    Text(
                        text = quotaSummary?.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = if (isLoading) "Loading remaining quota…" else "Tap refresh to check remaining quota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaBucketRow(bucket: QuotaBucketInfo) {
    val fraction = bucket.remainingFraction?.toFloat()?.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "bucket_${bucket.bucketId}"
    )
    val resetHint = formatResetCountdown(bucket.resetTime)
    val statusText = when {
        bucket.disabled -> "Disabled"
        fraction != null -> "${(fraction * 100).roundToInt()}%"
        else -> "—"
    }
    val barColor = quotaToneColor(fraction, bucket.disabled)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bucket.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!bucket.window.isNullOrBlank()) {
                    Text(
                        text = bucket.window,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (resetHint != null && (fraction == null || fraction < 0.995f)) {
                    "$statusText · $resetHint"
                } else {
                    statusText
                },
                style = MaterialTheme.typography.labelMedium,
                color = barColor
            )
        }
        if (!bucket.disabled && fraction != null) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

@Composable
private fun DynamicConfigOptionBlock(
    option: AcpConfigOption,
    quotaLookup: (value: String, name: String) -> ModelQuotaInfo?,
    onSelect: (String) -> Unit
) {
    val isModelOption = option.category == "model" || option.id == "model" || option.isLegacyModel

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(option.name, style = MaterialTheme.typography.titleMedium)
        if (option.description.isNotBlank()) {
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val choices = option.options
        val hasGroups = choices.any { it.group != null }

        if (!hasGroups && !isModelOption && choices.size in 2..5) {
            val selectedIndex = choices.indexOfFirst { it.value == option.currentValue }.coerceAtLeast(0)
            ConnectedButtonGroup(
                options = choices.map { it.name },
                selectedIndex = selectedIndex,
                onSelect = { idx -> onSelect(choices[idx].value) },
                modifier = Modifier.fillMaxWidth()
            )
            val currentDesc = choices.getOrNull(selectedIndex)?.description.orEmpty()
            if (currentDesc.isNotBlank()) {
                Text(
                    text = currentDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = choices.groupBy { it.group }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grouped.forEach { (groupName, groupChoices) ->
                    if (!groupName.isNullOrBlank()) {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    groupChoices.forEach { choice ->
                        val selected = option.currentValue == choice.value
                        val quota = if (isModelOption) quotaLookup(choice.value, choice.name) else null
                        val fraction = quota?.remainingFraction?.toFloat()?.coerceIn(0f, 1f)
                        val animatedQuota by animateFloatAsState(
                            targetValue = fraction ?: 0f,
                            animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
                            label = "model_quota_${choice.value}"
                        )

                        Surface(
                            onClick = { onSelect(choice.value) },
                            shape = M3ExpressiveShape.Large,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onSelect(choice.value) }
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp, end = 8.dp)
                                    ) {
                                        Text(choice.name, style = MaterialTheme.typography.bodyLarge)
                                        if (choice.description.isNotBlank()) {
                                            Text(
                                                text = choice.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (quota != null && (fraction != null || quota.disabled)) {
                                        ModelQuotaBadge(
                                            fraction = fraction,
                                            disabled = quota.disabled
                                        )
                                    }
                                }
                                if (quota != null && !quota.disabled && fraction != null) {
                                    val resetHint = formatResetCountdown(quota.resetTime)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { animatedQuota },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(3.dp)
                                                .clip(CircleShape),
                                            color = quotaToneColor(fraction, false),
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        if (resetHint != null && fraction < 0.995f) {
                                            Text(
                                                text = resetHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelQuotaBadge(fraction: Float?, disabled: Boolean) {
    val containerColor = when {
        disabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        fraction == null -> MaterialTheme.colorScheme.surfaceContainerHighest
        fraction <= 0.10f -> MaterialTheme.colorScheme.errorContainer
        fraction <= 0.30f -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        disabled -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction == null -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction <= 0.10f -> MaterialTheme.colorScheme.onErrorContainer
        fraction <= 0.30f -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val label = when {
        disabled -> "Disabled"
        fraction != null -> "${(fraction * 100).roundToInt()}%"
        else -> "—"
    }

    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun quotaToneColor(fraction: Float?, disabled: Boolean): Color {
    return when {
        disabled -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction == null -> MaterialTheme.colorScheme.onSurfaceVariant
        fraction <= 0.10f -> MaterialTheme.colorScheme.error
        fraction <= 0.30f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun formatResetCountdown(resetTimeIso: String?): String? {
    if (resetTimeIso.isNullOrBlank()) return null
    val resetInstant = runCatching { Instant.parse(resetTimeIso) }.getOrNull() ?: return null
    val remaining = Duration.between(Instant.now(), resetInstant)
    val mins = remaining.toMinutes()
    if (mins < 0) return null
    if (mins <= 1) return "Resetting soon"
    val days = mins / (60 * 24)
    val hours = (mins % (60 * 24)) / 60
    val remMins = mins % 60
    return when {
        days > 0 -> "Resets in ${days}d ${hours}h"
        hours > 0 -> "Resets in ${hours}h ${remMins}m"
        else -> "Resets in ${remMins}m"
    }
}
