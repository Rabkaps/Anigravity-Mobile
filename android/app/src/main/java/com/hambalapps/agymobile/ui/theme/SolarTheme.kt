package com.hambalapps.agymobile.ui.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    SOLAR("solar");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class SolarPhase(val label: String, val isDark: Boolean) {
    DAWN("Dawn", true),
    SUNRISE("Sunrise", false),
    MORNING("Morning", false),
    MIDDAY("Midday", false),
    GOLDEN_HOUR("Golden Hour", false),
    SUNSET("Sunset", true),
    TWILIGHT("Twilight", true),
    MIDNIGHT("Midnight", true)
}

val SolarDawnColorScheme = darkColorScheme(
    primary = Color(0xFFFF9ED2),
    onPrimary = Color(0xFF53133A),
    primaryContainer = Color(0xFF702851),
    onPrimaryContainer = Color(0xFFFFD8EC),
    secondary = Color(0xFFCBB4FF),
    onSecondary = Color(0xFF311F6B),
    secondaryContainer = Color(0xFF493685),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = Color(0xFF8AE0CC),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF145044),
    onTertiaryContainer = Color(0xFFAFF5E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1E1330),
    onBackground = Color(0xFFF0E4F7),
    surface = Color(0xFF1E1330),
    onSurface = Color(0xFFF0E4F7),
    surfaceVariant = Color(0xFF4E3D66),
    onSurfaceVariant = Color(0xFFD2C2E3),
    outline = Color(0xFF9A8AAC),
    outlineVariant = Color(0xFF4E3D66),
    surfaceContainerLowest = Color(0xFF140C22),
    surfaceContainerLow = Color(0xFF281B3E),
    surfaceContainer = Color(0xFF2F2049),
    surfaceContainerHigh = Color(0xFF372654),
    surfaceContainerHighest = Color(0xFF412E62)
)

val SolarSunriseColorScheme = lightColorScheme(
    primary = Color(0xFFD93B48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF41000B),
    secondary = Color(0xFFB85028),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3E1000),
    tertiary = Color(0xFF2A6E5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB0F1DF),
    onTertiaryContainer = Color(0xFF002119),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF0EB),
    onBackground = Color(0xFF271816),
    surface = Color(0xFFFFF0EB),
    onSurface = Color(0xFF271816),
    surfaceVariant = Color(0xFFF7D8D1),
    onSurfaceVariant = Color(0xFF57423D),
    outline = Color(0xFF8A716B),
    outlineVariant = Color(0xFFDDBEB7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFE4DC),
    surfaceContainer = Color(0xFFFCDCD3),
    surfaceContainerHigh = Color(0xFFFAD5CA),
    surfaceContainerHighest = Color(0xFFF5CBC0)
)

val SolarMorningColorScheme = lightColorScheme(
    primary = Color(0xFF007A5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3F2DB),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF3B6B8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE5FA),
    onSecondaryContainer = Color(0xFF001E30),
    tertiary = Color(0xFF7A6B1E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9E58B),
    onTertiaryContainer = Color(0xFF261F00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEFFBF5),
    onBackground = Color(0xFF13201B),
    surface = Color(0xFFEFFBF5),
    onSurface = Color(0xFF13201B),
    surfaceVariant = Color(0xFFD2E8DD),
    onSurfaceVariant = Color(0xFF3C4D45),
    outline = Color(0xFF6D7F76),
    outlineVariant = Color(0xFFB7CFC3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE0F5EA),
    surfaceContainer = Color(0xFFD8EFE4),
    surfaceContainerHigh = Color(0xFFD0ECDF),
    surfaceContainerHighest = Color(0xFFC6E5D6)
)

val SolarMiddayColorScheme = lightColorScheme(
    primary = Color(0xFF0B5ED7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001944),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1DFFF),
    onSecondaryContainer = Color(0xFF0E006A),
    tertiary = Color(0xFF007078),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9DF0F8),
    onTertiaryContainer = Color(0xFF002023),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEEF6FF),
    onBackground = Color(0xFF141C26),
    surface = Color(0xFFEEF6FF),
    onSurface = Color(0xFF141C26),
    surfaceVariant = Color(0xFFD7E2F2),
    onSurfaceVariant = Color(0xFF404856),
    outline = Color(0xFF707887),
    outlineVariant = Color(0xFFBDC7D8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFDFEEFF),
    surfaceContainer = Color(0xFFD7E8FD),
    surfaceContainerHigh = Color(0xFFCFE3FC),
    surfaceContainerHighest = Color(0xFFC4DBF9)
)

val SolarGoldenHourColorScheme = lightColorScheme(
    primary = Color(0xFFB55300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8BA),
    onPrimaryContainer = Color(0xFF391600),
    secondary = Color(0xFF9E424E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DC),
    onSecondaryContainer = Color(0xFF400A13),
    tertiary = Color(0xFF4E6B38),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0F0B6),
    onTertiaryContainer = Color(0xFF0D2100),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF4DE),
    onBackground = Color(0xFF241B0E),
    surface = Color(0xFFFFF4DE),
    onSurface = Color(0xFF241B0E),
    surfaceVariant = Color(0xFFF3DEC0),
    onSurfaceVariant = Color(0xFF524432),
    outline = Color(0xFF84745F),
    outlineVariant = Color(0xFFD6C2A7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFE9C2),
    surfaceContainer = Color(0xFFFDE2B5),
    surfaceContainerHigh = Color(0xFFFADBA6),
    surfaceContainerHighest = Color(0xFFF5D197)
)

val SolarSunsetColorScheme = darkColorScheme(
    primary = Color(0xFFFF9F5A),
    onPrimary = Color(0xFF521F00),
    primaryContainer = Color(0xFF743000),
    onPrimaryContainer = Color(0xFFFFDBBE),
    secondary = Color(0xFFFF94B2),
    onSecondary = Color(0xFF56132B),
    secondaryContainer = Color(0xFF722941),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFF7CF79),
    onTertiary = Color(0xFF422D00),
    tertiaryContainer = Color(0xFF5F4200),
    onTertiaryContainer = Color(0xFFFFE09E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF261215),
    onBackground = Color(0xFFF5E1E3),
    surface = Color(0xFF261215),
    onSurface = Color(0xFFF5E1E3),
    surfaceVariant = Color(0xFF563B40),
    onSurfaceVariant = Color(0xFFD9BEC2),
    outline = Color(0xFFA2888D),
    outlineVariant = Color(0xFF563B40),
    surfaceContainerLowest = Color(0xFF1A0B0D),
    surfaceContainerLow = Color(0xFF33191D),
    surfaceContainer = Color(0xFF3C1E23),
    surfaceContainerHigh = Color(0xFF462328),
    surfaceContainerHighest = Color(0xFF522A30)
)

val SolarTwilightColorScheme = darkColorScheme(
    primary = Color(0xFF6AE6E0),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = Color(0xFF90F7F1),
    secondary = Color(0xFF9CC2FF),
    onSecondary = Color(0xFF002E6B),
    secondaryContainer = Color(0xFF13448F),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFFB8B2FF),
    onTertiary = Color(0xFF221A66),
    tertiaryContainer = Color(0xFF3A3280),
    onTertiaryContainer = Color(0xFFE3DFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C1E2B),
    onBackground = Color(0xFFDDEBF5),
    surface = Color(0xFF0C1E2B),
    onSurface = Color(0xFFDDEBF5),
    surfaceVariant = Color(0xFF354A59),
    onSurfaceVariant = Color(0xFFB7CBD9),
    outline = Color(0xFF8195A3),
    outlineVariant = Color(0xFF354A59),
    surfaceContainerLowest = Color(0xFF07141E),
    surfaceContainerLow = Color(0xFF132A3B),
    surfaceContainer = Color(0xFF173145),
    surfaceContainerHigh = Color(0xFF1C394E),
    surfaceContainerHighest = Color(0xFF224259)
)

val SolarMidnightColorScheme = darkColorScheme(
    primary = Color(0xFFA2BFFF),
    onPrimary = Color(0xFF002C6B),
    primaryContainer = Color(0xFF0D4494),
    onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFFCEB9FF),
    onSecondary = Color(0xFF311F6B),
    secondaryContainer = Color(0xFF483684),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = Color(0xFF7CD6C7),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFF9DF2E3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF070914),
    onBackground = Color(0xFFE0E3F0),
    surface = Color(0xFF070914),
    onSurface = Color(0xFFE0E3F0),
    surfaceVariant = Color(0xFF3A4156),
    onSurfaceVariant = Color(0xFFBFC5D9),
    outline = Color(0xFF8990A3),
    outlineVariant = Color(0xFF3A4156),
    surfaceContainerLowest = Color(0xFF04050C),
    surfaceContainerLow = Color(0xFF0F1324),
    surfaceContainer = Color(0xFF141A2E),
    surfaceContainerHigh = Color(0xFF192038),
    surfaceContainerHighest = Color(0xFF202845)
)

fun solarColorSchemeForPhase(
    phase: SolarPhase,
    amoledAtNight: Boolean = false
): ColorScheme = when (phase) {
    SolarPhase.DAWN -> SolarDawnColorScheme
    SolarPhase.SUNRISE -> SolarSunriseColorScheme
    SolarPhase.MORNING -> SolarMorningColorScheme
    SolarPhase.MIDDAY -> SolarMiddayColorScheme
    SolarPhase.GOLDEN_HOUR -> SolarGoldenHourColorScheme
    SolarPhase.SUNSET -> SolarSunsetColorScheme
    SolarPhase.TWILIGHT -> SolarTwilightColorScheme
    SolarPhase.MIDNIGHT -> if (amoledAtNight) SolarMidnightColorScheme.toAmoledBlack() else SolarMidnightColorScheme
}

/**
 * Computes solar elevation angle (in degrees) using NOAA solar position equations.
 */
fun computeSolarElevationDeg(
    epochMillis: Long,
    latitudeDeg: Double,
    longitudeDeg: Double
): Double {
    val instant = Instant.ofEpochMilli(epochMillis)
    val utcDateTime = instant.atOffset(ZoneOffset.UTC)
    val dayOfYear = utcDateTime.dayOfYear
    val fractionalHour = utcDateTime.hour + utcDateTime.minute / 60.0 + utcDateTime.second / 3600.0

    val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1 + (fractionalHour - 12.0) / 24.0)

    val eqTime = 229.18 * (
        0.000075 +
            0.001868 * cos(gamma) -
            0.032077 * sin(gamma) -
            0.014615 * cos(2.0 * gamma) -
            0.040849 * sin(2.0 * gamma)
        )

    val decl = 0.006918 -
        0.399912 * cos(gamma) +
        0.070257 * sin(gamma) -
        0.006758 * cos(2.0 * gamma) +
        0.000907 * sin(2.0 * gamma) -
        0.002697 * cos(3.0 * gamma) +
        0.001480 * sin(3.0 * gamma)

    val timeOffsetMinutes = eqTime + 4.0 * longitudeDeg
    val trueSolarTimeMinutes = ((fractionalHour * 60.0 + timeOffsetMinutes) % 1440.0 + 1440.0) % 1440.0
    val hourAngleDeg = trueSolarTimeMinutes / 4.0 - 180.0
    val hourAngleRad = Math.toRadians(hourAngleDeg)
    val latRad = Math.toRadians(latitudeDeg)

    val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(hourAngleRad))
        .coerceIn(-1.0, 1.0)
    val zenithRad = acos(cosZenith)
    return 90.0 - Math.toDegrees(zenithRad)
}

/**
 * Resolves the 8-phase circadian Solar ColorScheme using coarse coordinates (when available)
 * or falls back cleanly to local wall-clock time, with smoothstep interpolation between phases.
 */
fun resolveSolarColorScheme(
    latitudeDeg: Double?,
    longitudeDeg: Double?,
    amoledAtNight: Boolean = false,
    epochMillis: Long = System.currentTimeMillis()
): Pair<SolarPhase, ColorScheme> {
    val midnightScheme = if (amoledAtNight) SolarMidnightColorScheme.toAmoledBlack() else SolarMidnightColorScheme

    if (latitudeDeg != null && longitudeDeg != null) {
        val instant = Instant.ofEpochMilli(epochMillis)
        val utcDateTime = instant.atOffset(ZoneOffset.UTC)
        val utcHour = utcDateTime.hour + utcDateTime.minute / 60.0
        val solarNoonOffsetHours = -longitudeDeg / 15.0
        val localSolarHour = ((utcHour - solarNoonOffsetHours) % 24.0 + 24.0) % 24.0
        val isMorningHalf = localSolarHour < 12.0

        val elev = computeSolarElevationDeg(epochMillis, latitudeDeg, longitudeDeg)

        return if (isMorningHalf) {
            when {
                elev < -10.0 -> SolarPhase.MIDNIGHT to midnightScheme
                elev < -4.0 -> blendPhases(
                    SolarPhase.MIDNIGHT, midnightScheme,
                    SolarPhase.DAWN, SolarDawnColorScheme,
                    ((elev + 10.0) / 6.0).toFloat()
                )
                elev < 2.0 -> blendPhases(
                    SolarPhase.DAWN, SolarDawnColorScheme,
                    SolarPhase.SUNRISE, SolarSunriseColorScheme,
                    ((elev + 4.0) / 6.0).toFloat()
                )
                elev < 14.0 -> blendPhases(
                    SolarPhase.SUNRISE, SolarSunriseColorScheme,
                    SolarPhase.MORNING, SolarMorningColorScheme,
                    ((elev - 2.0) / 12.0).toFloat()
                )
                elev < 28.0 -> blendPhases(
                    SolarPhase.MORNING, SolarMorningColorScheme,
                    SolarPhase.MIDDAY, SolarMiddayColorScheme,
                    ((elev - 14.0) / 14.0).toFloat()
                )
                else -> SolarPhase.MIDDAY to SolarMiddayColorScheme
            }
        } else {
            when {
                elev >= 26.0 -> SolarPhase.MIDDAY to SolarMiddayColorScheme
                elev >= 12.0 -> blendPhases(
                    SolarPhase.MIDDAY, SolarMiddayColorScheme,
                    SolarPhase.GOLDEN_HOUR, SolarGoldenHourColorScheme,
                    ((26.0 - elev) / 14.0).toFloat()
                )
                elev >= 2.0 -> blendPhases(
                    SolarPhase.GOLDEN_HOUR, SolarGoldenHourColorScheme,
                    SolarPhase.SUNSET, SolarSunsetColorScheme,
                    ((12.0 - elev) / 10.0).toFloat()
                )
                elev >= -5.0 -> blendPhases(
                    SolarPhase.SUNSET, SolarSunsetColorScheme,
                    SolarPhase.TWILIGHT, SolarTwilightColorScheme,
                    ((2.0 - elev) / 7.0).toFloat()
                )
                elev >= -12.0 -> blendPhases(
                    SolarPhase.TWILIGHT, SolarTwilightColorScheme,
                    SolarPhase.MIDNIGHT, midnightScheme,
                    ((-5.0 - elev) / 7.0).toFloat()
                )
                else -> SolarPhase.MIDNIGHT to midnightScheme
            }
        }
    }

    // Smooth continuous wall-clock fallback across 8 circadian anchors
    val now = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    val hour = now.hour + now.minute / 60f

    return when {
        hour < 4.5f -> SolarPhase.MIDNIGHT to midnightScheme
        hour < 6.0f -> blendPhases(
            SolarPhase.MIDNIGHT, midnightScheme,
            SolarPhase.DAWN, SolarDawnColorScheme,
            (hour - 4.5f) / 1.5f
        )
        hour < 7.5f -> blendPhases(
            SolarPhase.DAWN, SolarDawnColorScheme,
            SolarPhase.SUNRISE, SolarSunriseColorScheme,
            (hour - 6.0f) / 1.5f
        )
        hour < 10.0f -> blendPhases(
            SolarPhase.SUNRISE, SolarSunriseColorScheme,
            SolarPhase.MORNING, SolarMorningColorScheme,
            (hour - 7.5f) / 2.5f
        )
        hour < 12.5f -> blendPhases(
            SolarPhase.MORNING, SolarMorningColorScheme,
            SolarPhase.MIDDAY, SolarMiddayColorScheme,
            (hour - 10.0f) / 2.5f
        )
        hour < 15.5f -> SolarPhase.MIDDAY to SolarMiddayColorScheme
        hour < 17.5f -> blendPhases(
            SolarPhase.MIDDAY, SolarMiddayColorScheme,
            SolarPhase.GOLDEN_HOUR, SolarGoldenHourColorScheme,
            (hour - 15.5f) / 2.0f
        )
        hour < 19.0f -> blendPhases(
            SolarPhase.GOLDEN_HOUR, SolarGoldenHourColorScheme,
            SolarPhase.SUNSET, SolarSunsetColorScheme,
            (hour - 17.5f) / 1.5f
        )
        hour < 20.5f -> blendPhases(
            SolarPhase.SUNSET, SolarSunsetColorScheme,
            SolarPhase.TWILIGHT, SolarTwilightColorScheme,
            (hour - 19.0f) / 1.5f
        )
        hour < 22.0f -> blendPhases(
            SolarPhase.TWILIGHT, SolarTwilightColorScheme,
            SolarPhase.MIDNIGHT, midnightScheme,
            (hour - 20.5f) / 1.5f
        )
        else -> SolarPhase.MIDNIGHT to midnightScheme
    }
}

private fun blendPhases(
    fromPhase: SolarPhase,
    fromScheme: ColorScheme,
    toPhase: SolarPhase,
    toScheme: ColorScheme,
    rawFraction: Float
): Pair<SolarPhase, ColorScheme> {
    val t = smoothstep(rawFraction)
    val activePhase = if (t < 0.5f) fromPhase else toPhase
    val blended = lerpColorScheme(fromScheme, toScheme, t)
    // Avoid dark<->light midpoint gray-on-gray text when one scheme is dark and the other is light:
    // keep high-contrast foreground tokens from the dominant phase while smoothly interpolating surfaces.
    val fromIsLight = fromScheme.background.luminance() > 0.5f
    val toIsLight = toScheme.background.luminance() > 0.5f
    val safeScheme = if (fromIsLight != toIsLight) {
        val dominant = if (t < 0.5f) fromScheme else toScheme
        blended.copy(
            onPrimary = dominant.onPrimary,
            onPrimaryContainer = dominant.onPrimaryContainer,
            onSecondary = dominant.onSecondary,
            onSecondaryContainer = dominant.onSecondaryContainer,
            onTertiary = dominant.onTertiary,
            onTertiaryContainer = dominant.onTertiaryContainer,
            onBackground = dominant.onBackground,
            onSurface = dominant.onSurface,
            onSurfaceVariant = dominant.onSurfaceVariant,
            onError = dominant.onError,
            onErrorContainer = dominant.onErrorContainer
        )
    } else {
        blended
    }
    return activePhase to safeScheme
}

private fun smoothstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

fun getLastKnownCoarseLocation(context: Context): Pair<Double, Double>? {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val best = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        best?.let { it.latitude to it.longitude }
    } catch (_: SecurityException) {
        null
    }
}

private fun lerpColorScheme(a: ColorScheme, b: ColorScheme, fraction: Float): ColorScheme {
    val t = fraction.coerceIn(0f, 1f)
    return a.copy(
        primary = lerp(a.primary, b.primary, t),
        onPrimary = lerp(a.onPrimary, b.onPrimary, t),
        primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
        onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
        secondary = lerp(a.secondary, b.secondary, t),
        onSecondary = lerp(a.onSecondary, b.onSecondary, t),
        secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, t),
        onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, t),
        tertiary = lerp(a.tertiary, b.tertiary, t),
        onTertiary = lerp(a.onTertiary, b.onTertiary, t),
        tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, t),
        onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, t),
        background = lerp(a.background, b.background, t),
        onBackground = lerp(a.onBackground, b.onBackground, t),
        surface = lerp(a.surface, b.surface, t),
        onSurface = lerp(a.onSurface, b.onSurface, t),
        surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
        onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
        outline = lerp(a.outline, b.outline, t),
        outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
        surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
        surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
        surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
        surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
        error = lerp(a.error, b.error, t),
        onError = lerp(a.onError, b.onError, t),
        errorContainer = lerp(a.errorContainer, b.errorContainer, t),
        onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, t)
    )
}
