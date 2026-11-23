package com.hambalapps.agymobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion

/**
 * Send <-> Stop action button that morphs its corner radius and container color
 * using M3 Expressive spatial spring physics.
 */
@Composable
fun MorphingActionButton(
    isStreaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isStreaming) 15.dp else 24.dp,
        animationSpec = M3ExpressiveMotion.adaptiveSpatialFast(),
        label = "actionMorphRadius"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isStreaming -> MaterialTheme.colorScheme.errorContainer
            enabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "actionMorphBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isStreaming -> MaterialTheme.colorScheme.onErrorContainer
            enabled -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = M3ExpressiveMotion.adaptiveEffectsFast(),
        label = "actionMorphFg"
    )

    Surface(
        onClick = { if (isStreaming) onStop() else if (enabled) onSend() },
        enabled = isStreaming || enabled,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (isStreaming) "Stop generation" else "Send prompt"
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isStreaming || enabled) 3.dp else 0.dp
    ) {
        AnimatedContent(
            targetState = isStreaming,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.72f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.72f))
            },
            contentAlignment = Alignment.Center,
            label = "actionIconSwap",
            modifier = Modifier.fillMaxSize()
        ) { streaming ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (streaming) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
