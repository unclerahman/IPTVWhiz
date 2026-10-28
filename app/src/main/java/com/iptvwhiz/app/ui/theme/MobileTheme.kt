package com.iptvwhiz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    primaryContainer = Brand.IndigoBorder,
    onPrimaryContainer = Brand.IndigoPale,
    inversePrimary = Brand.Indigo,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnSecondary,
    secondaryContainer = Brand.Indigo,
    onSecondaryContainer = Brand.IndigoPale,
    tertiary = Brand.Live,
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Brand.LiveDeep,
    onTertiaryContainer = Brand.LivePale,
    background = Brand.DarkBackground,
    onBackground = Brand.DarkOnSurface,
    surface = Brand.DarkBackground,
    onSurface = Brand.DarkOnSurface,
    surfaceVariant = Brand.DarkSurfaceVariant,
    onSurfaceVariant = Brand.DarkOnSurfaceVariant,
    surfaceTint = Brand.Primary,
    inverseSurface = Brand.DarkOnSurface,
    inverseOnSurface = Brand.DarkBackground,
    error = Brand.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Brand.ErrorContainer,
    onErrorContainer = Brand.OnErrorContainer,
    outline = Brand.DarkOutline,
    outlineVariant = Brand.DarkOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = Brand.DarkSurfaceContainerHighest,
    surfaceDim = Brand.DarkBackground,
    surfaceContainerLowest = Color(0xFF0A0A18),
    surfaceContainerLow = Brand.DarkSurfaceContainerLow,
    surfaceContainer = Brand.DarkSurface,
    surfaceContainerHigh = Brand.DarkSurfaceContainerHigh,
    surfaceContainerHighest = Brand.DarkSurfaceContainerHighest,
)

private val LightColors = lightColorScheme(
    primary = Brand.PrimaryLight,
    onPrimary = Brand.OnPrimaryLight,
    primaryContainer = Brand.IndigoPale,
    onPrimaryContainer = Brand.IndigoDeep,
    secondary = Brand.Indigo,
    onSecondary = Brand.OnPrimaryLight,
    secondaryContainer = Brand.IndigoPale,
    onSecondaryContainer = Brand.IndigoDeep,
    tertiary = Color(0xFF15803D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Brand.LivePale,
    onTertiaryContainer = Brand.LiveDeep,
    background = Brand.LightBackground,
    onBackground = Brand.LightOnSurface,
    surface = Brand.LightSurface,
    onSurface = Brand.LightOnSurface,
    surfaceVariant = Brand.LightSurfaceVariant,
    onSurfaceVariant = Brand.LightOnSurfaceVariant,
    surfaceContainer = Brand.LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2E0F0),
    surfaceContainerHighest = Color(0xFFD9D7E8),
    outline = Color(0xFFC7C9D9),
    outlineVariant = Color(0xFFE2E2EC),
    error = Brand.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Brand.ErrorContainer,
)

/**
 * Slightly tighter than the Material defaults: Inter-like tracking and medium weights, without
 * bundling a font file. The system sans (Roboto) keeps the APK small and native on Android.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

/**
 * Phones are dark-only, matching the TV layout and the design system's "Dark Mode (OLED)" style:
 * a midnight background keeps the video the brightest thing on screen. [darkTheme] stays a
 * parameter so a future Appearance setting can offer the light scheme.
 */
@Composable
fun IptvWhizMobileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
