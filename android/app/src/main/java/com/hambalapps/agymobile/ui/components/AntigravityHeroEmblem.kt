package com.hambalapps.agymobile.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.hambalapps.agymobile.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.sin

private data class StarSeed(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val isSparkle: Boolean,
    val speedY: Float,
    val speedX: Float,
    val baseAlpha: Float,
    val twinklePhase: Float,
    val colorSlot: Int
)

/**
 * Ambient field of faint, slowly drifting stars and micro-sparkles behind the home hero.
 * Reads animation state inside Canvas (Draw phase) and reuses a single Path for zero recompositions.
 */
@Composable
fun AntigravityStarfieldBackground(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val isLightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val stars = remember {
        List(46) { i ->
            val h1 = ((i * 73 + 19) % 97) / 97f
            val h2 = ((i * 41 + 53) % 89) / 89f
            val h3 = ((i * 29 + 11) % 61) / 61f
            val isSparkle = i % 5 == 0
            StarSeed(
                xFrac = h1,
                yFrac = h2,
                radiusDp = if (isSparkle) 2.2f + h3 * 1.4f else 0.9f + h3 * 1.1f,
                isSparkle = isSparkle,
                speedY = 0.08f + h3 * 0.14f,
                speedX = (h1 - 0.5f) * 0.06f,
                baseAlpha = if (isSparkle) 0.18f + h3 * 0.10f else 0.10f + h3 * 0.14f,
                twinklePhase = h2 * 2f * PI.toFloat(),
                colorSlot = i % 3
            )
        }
    }

    val reducedMotion = LocalReducedMotion.current
    val driftState = if (reducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "antigravityStarfield")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 28_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "starDrift"
        )
    }
    val starPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val driftProgress = driftState.value
        val alphaScale = if (isLightSurface) 0.72f else 1.0f

        stars.forEach { star ->
            val x = (((star.xFrac + driftProgress * star.speedX) % 1f) + 1f) % 1f * w
            val y = (((star.yFrac - driftProgress * star.speedY) % 1f) + 1f) % 1f * h
            val twinkle = if (reducedMotion) {
                0.85f
            } else {
                0.58f + 0.42f * sin(driftProgress * 2f * PI.toFloat() * 3f + star.twinklePhase)
            }
            val color = when (star.colorSlot) {
                0 -> primary
                1 -> secondary
                else -> tertiary
            }.copy(alpha = (star.baseAlpha * twinkle * alphaScale).coerceIn(0f, 0.35f))

            val pos = Offset(x, y)
            val r = star.radiusDp.dp.toPx()
            if (star.isSparkle) {
                fillFourPointStarPath(starPath, pos, r * 1.45f, r * 0.36f)
                drawPath(path = starPath, color = color)
            } else {
                drawCircle(
                    color = color,
                    radius = r,
                    center = pos
                )
            }
        }
    }
}

/**
 * Seamless, borderless Antigravity starlight emblem.
 * Reads pulse state inside Canvas (Draw phase) and reuses a single Path for zero recompositions.
 */
@Composable
fun AntigravityHeroEmblem(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val isLightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val reducedMotion = LocalReducedMotion.current
    val pulseState = if (reducedMotion) {
        remember { mutableFloatStateOf(1f) }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "antigravityStarPulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.86f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "starPulse"
        )
    }

    val coreStarStart = if (isLightSurface) {
        primary
    } else {
        lerp(primary, Color.White, 0.72f)
    }
    val coreStarEnd = if (isLightSurface) {
        lerp(secondary, primary, 0.40f)
    } else {
        lerp(secondary, Color.White, 0.42f)
    }
    val starPath = remember { Path() }

    Canvas(modifier = modifier.size(188.dp)) {
        val pulse = pulseState.value
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val minDim = size.minDimension

        // 1. Wide ambient atmospheric glow dissolving smoothly to Transparent
        val ambientRadius = minDim * 0.49f
        val ambientAlpha = (if (isLightSurface) 0.28f else 0.38f) * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to primary.copy(alpha = ambientAlpha),
                    0.24f to primary.copy(alpha = ambientAlpha * 0.68f),
                    0.50f to secondary.copy(alpha = ambientAlpha * 0.30f),
                    0.78f to tertiary.copy(alpha = ambientAlpha * 0.09f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = ambientRadius
            ),
            radius = ambientRadius,
            center = center
        )

        // 2. Two soft off-center nebula wisps for organic depth
        val wisp1Center = Offset(cx + minDim * 0.10f, cy - minDim * 0.08f)
        val wisp1Radius = minDim * 0.32f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = 0.18f * pulse),
                    Color.Transparent
                ),
                center = wisp1Center,
                radius = wisp1Radius
            ),
            radius = wisp1Radius,
            center = wisp1Center
        )

        val wisp2Center = Offset(cx - minDim * 0.11f, cy + minDim * 0.09f)
        val wisp2Radius = minDim * 0.28f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiary.copy(alpha = 0.16f * pulse),
                    Color.Transparent
                ),
                center = wisp2Center,
                radius = wisp2Radius
            ),
            radius = wisp2Radius,
            center = wisp2Center
        )

        // 3. Focused luminous core bloom right behind the main star
        val coreRadius = minDim * 0.26f
        val coreAlpha = (if (isLightSurface) 0.36f else 0.56f) * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to coreStarStart.copy(alpha = coreAlpha),
                    0.42f to primary.copy(alpha = coreAlpha * 0.48f),
                    1.0f to Color.Transparent
                ),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )

        // 4. Main central 4-point star + outer soft halo + bright inner glint
        val mainOuter = minDim * 0.175f
        val mainInner = mainOuter * 0.29f
        fillFourPointStarPath(starPath, center, mainOuter * 1.22f, mainInner * 1.22f)
        drawPath(
            path = starPath,
            color = primary.copy(alpha = (if (isLightSurface) 0.22f else 0.34f) * pulse)
        )

        fillFourPointStarPath(starPath, center, mainOuter, mainInner)
        drawPath(
            path = starPath,
            brush = Brush.linearGradient(
                colors = listOf(coreStarStart, coreStarEnd),
                start = Offset(cx - mainOuter, cy - mainOuter),
                end = Offset(cx + mainOuter, cy + mainOuter)
            )
        )

        fillFourPointStarPath(starPath, center, mainOuter * 0.48f, mainInner * 0.48f)
        drawPath(
            path = starPath,
            color = if (isLightSurface) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.85f)
        )

        // 5. Upper-right companion 4-point sparkle
        val companionCenter = Offset(cx + minDim * 0.145f, cy - minDim * 0.135f)
        val companionOuter = mainOuter * 0.38f
        fillFourPointStarPath(starPath, companionCenter, companionOuter, companionOuter * 0.30f)
        drawPath(
            path = starPath,
            color = coreStarStart.copy(alpha = 0.88f * pulse)
        )

        // 6. Lower-left micro 4-point sparkle
        val microCenter = Offset(cx - minDim * 0.14f, cy + minDim * 0.13f)
        val microOuter = mainOuter * 0.24f
        fillFourPointStarPath(starPath, microCenter, microOuter, microOuter * 0.32f)
        drawPath(
            path = starPath,
            color = secondary.copy(alpha = 0.68f * pulse)
        )

        // 7. Delicate fading starlight dust motes radiating outward
        val dustMotes = listOf(
            Triple(Offset(cx - minDim * 0.21f, cy - minDim * 0.08f), 2.1.dp.toPx(), 0.48f),
            Triple(Offset(cx + minDim * 0.22f, cy + minDim * 0.07f), 1.8.dp.toPx(), 0.38f),
            Triple(Offset(cx - minDim * 0.04f, cy - minDim * 0.24f), 1.6.dp.toPx(), 0.30f),
            Triple(Offset(cx + minDim * 0.06f, cy + minDim * 0.23f), 1.5.dp.toPx(), 0.22f)
        )
        dustMotes.forEach { (pos, radius, alpha) ->
            drawCircle(
                color = primary.copy(alpha = alpha * pulse),
                radius = radius,
                center = pos
            )
        }
    }
}

private fun fillFourPointStarPath(
    path: Path,
    center: Offset,
    outerRadius: Float,
    innerRadius: Float
) {
    val cx = center.x
    val cy = center.y
    val diag = innerRadius * 0.70710677f

    path.reset()
    path.moveTo(cx, cy - outerRadius)
    path.quadraticTo(cx, cy, cx + diag, cy - diag)
    path.quadraticTo(cx, cy, cx + outerRadius, cy)
    path.quadraticTo(cx, cy, cx + diag, cy + diag)
    path.quadraticTo(cx, cy, cx, cy + outerRadius)
    path.quadraticTo(cx, cy, cx - diag, cy + diag)
    path.quadraticTo(cx, cy, cx - outerRadius, cy)
    path.quadraticTo(cx, cy, cx - diag, cy - diag)
    path.quadraticTo(cx, cy, cx, cy - outerRadius)
    path.close()
}
