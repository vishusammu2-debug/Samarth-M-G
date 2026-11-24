package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ProtonDarkColorScheme = darkColorScheme(
    primary = ProtonCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = HudPanelElevated,
    onPrimaryContainer = ProtonIceBlue,
    secondary = ProtonElectricBlue,
    onSecondary = ObsidianVoid,
    secondaryContainer = DeepSpaceNavy,
    onSecondaryContainer = HudTextPrimary,
    tertiary = ProtonEmerald,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = HudTextPrimary,
    surface = DeepSpaceNavy,
    onSurface = HudTextPrimary,
    surfaceVariant = HudPanelSurface,
    onSurfaceVariant = HudTextSecondary,
    outline = HudBorderCyan,
    outlineVariant = HudBorderBright,
    error = ProtonCrimson,
    onError = ObsidianVoid
)

private val ProtonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ProtonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProtonDarkColorScheme,
        typography = ProtonTypography,
        shapes = ProtonShapes,
        content = content
    )
}
