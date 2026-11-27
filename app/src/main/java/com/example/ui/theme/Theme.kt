package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class WatchTogetherExtras(
    val glassSurface: Color,
    val glassBorder: Color,
    val liveBadge: Color,
    val onlineGreen: Color,
    val degradedAmber: Color,
    val offlineSlate: Color,
    val hostGold: Color,
    val syncCyan: Color
)

val LocalWatchTogetherExtras = staticCompositionLocalOf {
    WatchTogetherExtras(
        glassSurface = VelvetSurfaceVariant.copy(alpha = 0.78f),
        glassBorder = GlassBorderDark,
        liveBadge = LiveRed,
        onlineGreen = OnlineGreen,
        degradedAmber = DegradedAmber,
        offlineSlate = OfflineSlate,
        hostGold = ProjectorGold,
        syncCyan = SyncCyan
    )
}

private val WatchTogetherDarkColorScheme = darkColorScheme(
    primary = CinemaCrimson,
    onPrimary = Color.White,
    primaryContainer = CinemaCrimsonContainer,
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = ProjectorGold,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = ProjectorGoldContainer,
    onSecondaryContainer = Color(0xFFFFE5A3),
    tertiary = SyncCyan,
    onTertiary = Color(0xFF00201D),
    tertiaryContainer = SyncCyanContainer,
    onTertiaryContainer = Color(0xFFA7F3EC),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = VelvetSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = VelvetSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = VelvetSurfaceHigh,
    outline = GlassBorderDark,
    error = ErrorRose,
    onError = Color(0xFF2B000A)
)

private val WatchTogetherLightColorScheme = lightColorScheme(
    primary = CinemaCrimsonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1),
    onPrimaryContainer = Color(0xFF3F0016),
    secondary = Color(0xFFB87D00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBB8),
    onSecondaryContainer = Color(0xFF2B1C00),
    tertiary = Color(0xFF0E8F84),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4F5EF),
    onTertiaryContainer = Color(0xFF00201D),
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = Color(0xFFE5E8F2),
    outline = DaylightBorder,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val WatchTogetherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun WatchTogetherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WatchTogetherDarkColorScheme else WatchTogetherLightColorScheme
    val extras = if (darkTheme) {
        WatchTogetherExtras(
            glassSurface = VelvetSurfaceVariant.copy(alpha = 0.80f),
            glassBorder = GlassBorderDark,
            liveBadge = LiveRed,
            onlineGreen = OnlineGreen,
            degradedAmber = DegradedAmber,
            offlineSlate = OfflineSlate,
            hostGold = ProjectorGold,
            syncCyan = SyncCyan
        )
    } else {
        WatchTogetherExtras(
            glassSurface = DaylightSurface.copy(alpha = 0.90f),
            glassBorder = DaylightBorder,
            liveBadge = LiveRed,
            onlineGreen = Color(0xFF16A34A),
            degradedAmber = Color(0xFFD97706),
            offlineSlate = OfflineSlate,
            hostGold = Color(0xFFB87D00),
            syncCyan = Color(0xFF0E8F84)
        )
    }

    CompositionLocalProvider(LocalWatchTogetherExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WatchTogetherTypography,
            shapes = WatchTogetherShapes,
            content = content
        )
    }
}

fun parseHexColor(hex: String, fallback: Color = CinemaCrimson): Color {
    return try {
        val clean = hex.removePrefix("#")
        if (clean.length == 6) {
            Color(("FF$clean").toLong(16))
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
