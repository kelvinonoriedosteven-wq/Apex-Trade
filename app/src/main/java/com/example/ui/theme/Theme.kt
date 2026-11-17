package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ApexDarkColorScheme = darkColorScheme(
    primary = EmeraldAlpha,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldAlphaDim,
    onPrimaryContainer = EmeraldAlpha,
    secondary = ChampagneGold,
    onSecondary = ObsidianBg,
    secondaryContainer = ChampagneGoldDim,
    onSecondaryContainer = ChampagneGold,
    tertiary = SkySignal,
    onTertiary = ObsidianBg,
    tertiaryContainer = SkySignalDim,
    onTertiaryContainer = SkySignal,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = CrimsonLoss,
    onError = TextPrimary,
    errorContainer = CrimsonLossDim,
    onErrorContainer = CrimsonLoss
)

private val ApexShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ApexTradeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = Typography,
        shapes = ApexShapes,
        content = content
    )
}
