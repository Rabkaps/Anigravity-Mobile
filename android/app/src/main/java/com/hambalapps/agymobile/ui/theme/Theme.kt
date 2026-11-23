package com.hambalapps.agymobile.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun AgyExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledDark: Boolean = false,
    forceReducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AgyDarkColorScheme
        else -> AgyLightColorScheme
    }

    val colorScheme = if (darkTheme && amoledDark) {
        baseScheme.toAmoledBlack()
    } else {
        baseScheme
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

    CompositionLocalProvider(LocalReducedMotion provides (forceReducedMotion || systemReducedMotion)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AgyExpressiveTypography,
            shapes = AgyExpressiveShapes,
            content = content
        )
    }
}
