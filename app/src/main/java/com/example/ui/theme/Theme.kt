package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF04131A),
    primaryContainer = Color(0xFF0D2E3A),
    onPrimaryContainer = ElectricCyan,
    secondary = AmberLogic,
    onSecondary = Color(0xFF1A1100),
    secondaryContainer = Color(0xFF2E2208),
    onSecondaryContainer = AmberLogic,
    tertiary = EmeraldRun,
    onTertiary = Color(0xFF031911),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateElevated,
    surfaceContainerLow = SlateWell,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = RoseAlert,
    onError = Color.White
)

private val StudioShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
)

@Composable
fun ShizuMacroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
