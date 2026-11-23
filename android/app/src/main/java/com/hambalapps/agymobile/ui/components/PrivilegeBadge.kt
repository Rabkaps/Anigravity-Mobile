package com.hambalapps.agymobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion

enum class BridgeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@Composable
fun PrivilegeBadge(
    privilegeLevel: String,
    connectionState: BridgeConnectionState,
    isRemote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        connectionState == BridgeConnectionState.DISCONNECTED -> "OFFLINE"
        connectionState == BridgeConnectionState.CONNECTING -> "SYNC…"
        connectionState == BridgeConnectionState.ERROR -> "ERROR"
        isRemote -> "REMOTE"
        privilegeLevel == "root" -> "ROOT"
        privilegeLevel == "shizuku" -> "SHIZUKU"
        privilegeLevel == "termux-api" -> "TERMUX"
        else -> "SHELL"
    }

    val targetContainer = when (label) {
        "ROOT" -> MaterialTheme.colorScheme.tertiaryContainer
        "SHIZUKU" -> MaterialTheme.colorScheme.primaryContainer
        "REMOTE" -> MaterialTheme.colorScheme.secondaryContainer
        "ERROR" -> MaterialTheme.colorScheme.errorContainer
        "OFFLINE" -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val targetContent = when (label) {
        "ROOT" -> MaterialTheme.colorScheme.onTertiaryContainer
        "SHIZUKU" -> MaterialTheme.colorScheme.onPrimaryContainer
        "REMOTE" -> MaterialTheme.colorScheme.onSecondaryContainer
        "ERROR" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "privBadgeBg"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "privBadgeFg"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
