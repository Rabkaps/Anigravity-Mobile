package com.hambalapps.agymobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.M3ExpressiveMotion
import kotlin.math.roundToInt

/**
 * Circular context window usage indicator matching agy's `/context` token ring.
 */
@Composable
fun ContextRingIndicator(
    usageRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = usageRatio.coerceIn(0f, 1f)
    val animatedSweep by animateFloatAsState(
        targetValue = clamped,
        animationSpec = M3ExpressiveMotion.adaptiveSpatialDefault(),
        label = "contextSweep"
    )

    val ringColor = when {
        clamped >= 0.85f -> MaterialTheme.colorScheme.error
        clamped >= 0.65f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val pct = (clamped * 100f).roundToInt()

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = "Context usage $pct percent" }
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(30.dp)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedSweep,
                    useCenter = false,
                    style = stroke
                )
            }
            Text(
                text = "$pct",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
