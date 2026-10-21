package com.jaivish.gowork.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GoColors {
    val Ink = Color(0xFF0B0B0F)
    val Panel = Color(0xFF15151C)
    val PanelHigh = Color(0xFF1E1E28)
    val Stroke = Color(0xFF272734)
    val Volt = Color(0xFFFFD60A)
    val Flame = Color(0xFFFF453A)
    val Mint = Color(0xFF32D74B)
    val Sky = Color(0xFF64D2FF)
    val Muted = Color(0xFF9D9DAC)
    val Faint = Color(0xFF62626F)
    val Insta = Color(0xFFE1306C)
    val Youtube = Color(0xFFFF2D2D)
}

private val Scheme = darkColorScheme(
    primary = GoColors.Volt,
    onPrimary = GoColors.Ink,
    primaryContainer = GoColors.PanelHigh,
    onPrimaryContainer = GoColors.Volt,
    secondary = GoColors.Sky,
    onSecondary = GoColors.Ink,
    tertiary = GoColors.Mint,
    background = GoColors.Ink,
    onBackground = Color.White,
    surface = GoColors.Panel,
    onSurface = Color.White,
    surfaceVariant = GoColors.PanelHigh,
    onSurfaceVariant = GoColors.Muted,
    outline = GoColors.Stroke,
    outlineVariant = GoColors.Stroke,
    error = GoColors.Flame,
    onError = Color.White,
)

private val Base = Typography()

private val GoTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

@Composable
fun GoWorkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = GoTypography, content = content)
}
