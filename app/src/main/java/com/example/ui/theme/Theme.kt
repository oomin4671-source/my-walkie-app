package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WalkieDarkColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = ObsidianBg,
    primaryContainer = SignalOrangePressed,
    onPrimaryContainer = TextPrimary,
    secondary = RadioCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = RadioCyanMuted,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldOnline,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = SteelSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = SteelSurface,
    surfaceContainerHigh = ElevatedSteel,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = CrimsonAlert,
    onError = TextPrimary
)

val WalkieShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyWalkieTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WalkieDarkColorScheme,
        typography = WalkieTypography,
        shapes = WalkieShapes,
        content = content
    )
}
