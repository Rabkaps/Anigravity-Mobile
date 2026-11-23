package com.hambalapps.agymobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback Expressive HCT-inspired Antigravity palette (used on Android 8-11 or when Dynamic Color is off)
val AgyLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A56DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF6B3FD9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF006B5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9DF2E3),
    onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FD),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFF8F9FD),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4FA),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE6E8F0),
    surfaceContainerHighest = Color(0xFFE0E2EA)
)

val AgyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9EC0FF),
    onPrimary = Color(0xFF002D70),
    primaryContainer = Color(0xFF00439E),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFD0BCFF),
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF7CD6C7),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFF9DF2E3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE2E2EA),
    surface = Color(0xFF101319),
    onSurface = Color(0xFFE2E2EA),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF171B23),
    surfaceContainer = Color(0xFF1C2029),
    surfaceContainerHigh = Color(0xFF262B35),
    surfaceContainerHighest = Color(0xFF313642)
)

fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0D10),
    surfaceContainer = Color(0xFF13151B),
    surfaceContainerHigh = Color(0xFF1C1F27),
    surfaceContainerHighest = Color(0xFF262A34)
)
