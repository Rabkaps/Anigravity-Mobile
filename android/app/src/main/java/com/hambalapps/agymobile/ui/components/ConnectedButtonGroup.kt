package com.hambalapps.agymobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion

@Composable
fun ConnectedButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spatialSpec = M3ExpressiveMotion.adaptiveSpatialFast<Dp>()
    val effectsSpec = M3ExpressiveMotion.adaptiveEffectsFast<Color>()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val isFirst = index == 0
            val isLast = index == options.lastIndex

            val startRadius by animateDpAsState(
                targetValue = when {
                    isFirst -> 22.dp
                    isSelected -> 12.dp
                    else -> 4.dp
                },
                animationSpec = spatialSpec,
                label = "connectedStartRadius"
            )
            val endRadius by animateDpAsState(
                targetValue = when {
                    isLast -> 22.dp
                    isSelected -> 12.dp
                    else -> 4.dp
                },
                animationSpec = spatialSpec,
                label = "connectedEndRadius"
            )

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = effectsSpec,
                label = "connectedBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = effectsSpec,
                label = "connectedFg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = effectsSpec,
                label = "connectedBorder"
            )

            OutlinedButton(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
