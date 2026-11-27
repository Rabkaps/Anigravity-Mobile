package com.hambalapps.agymobile.ui.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AgyExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledDark: Boolean = false,
    forceReducedMotion: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    solarLocation: Pair<Double, Double>? = null,
    solarPhaseOverride: SolarPhase? = null,
    solarEpochMillis: Long = System.currentTimeMillis(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = remember(
        themeMode,
        solarLocation,
        solarEpochMillis,
        amoledDark,
        solarPhaseOverride,
        darkTheme,
        dynamicColor
    ) {
        when {
            solarPhaseOverride != null -> {
                solarColorSchemeForPhase(solarPhaseOverride, amoledAtNight = amoledDark)
            }
            themeMode == AppThemeMode.SOLAR -> {
                resolveSolarColorScheme(
                    latitudeDeg = solarLocation?.first,
                    longitudeDeg = solarLocation?.second,
                    amoledAtNight = amoledDark,
                    epochMillis = solarEpochMillis
                ).second
            }
            else -> {
                val effectiveDark = when (themeMode) {
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                    AppThemeMode.SYSTEM, AppThemeMode.SOLAR -> darkTheme
                }
                val baseScheme = when {
                    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    effectiveDark -> AgyDarkColorScheme
                    else -> AgyLightColorScheme
                }
                if (effectiveDark && amoledDark) baseScheme.toAmoledBlack() else baseScheme
            }
        }
    }

    val systemReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val view = LocalView.current
    val reducedMotion = forceReducedMotion || systemReducedMotion
    val animatedScheme = if (view.isInEditMode || reducedMotion) {
        colorScheme
    } else {
        animateColorSchemeAsState(colorScheme)
    }

    val isLightCanvas = animatedScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLightCanvas
            controller.isAppearanceLightNavigationBars = isLightCanvas
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = AgyExpressiveTypography,
            shapes = AgyExpressiveShapes,
            content = content
        )
    }
}

@Composable
private fun animateColorSchemeAsState(target: ColorScheme): ColorScheme {
    val spec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = 1200,
        easing = FastOutSlowInEasing
    )
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, spec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, spec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, spec, label = "onTertiary").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "outlineVariant").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "surfaceContainerLowest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "surfaceContainerLow").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "surfaceContainerHighest").value
    )
}
