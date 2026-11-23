package com.hambalapps.agymobile.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Material 3 Expressive spring physics specs.
 * Spatial springs provide expressive overshoot & bounce for layout/shape motion;
 * Effects springs are critically damped (no overshoot) for color, alpha, and elevation.
 */
object M3ExpressiveMotion {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )

    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessVeryLow
    )

    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> effectsDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    @Composable
    fun <T> adaptiveSpatialFast(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spatialFast()

    @Composable
    fun <T> adaptiveSpatialDefault(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spatialDefault()

    @Composable
    fun <T> adaptiveEffectsFast(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else effectsFast()

    @Composable
    fun <T> adaptiveEffectsDefault(): AnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else effectsDefault()
}
