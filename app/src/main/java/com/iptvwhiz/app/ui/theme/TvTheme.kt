package com.iptvwhiz.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    primaryContainer = Brand.IndigoBorder,
    onPrimaryContainer = Brand.IndigoPale,
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
    surface = Brand.DarkSurface,
    onSurface = Brand.DarkOnSurface,
    surfaceVariant = Brand.DarkSurfaceVariant,
    onSurfaceVariant = Brand.DarkOnSurfaceVariant,
    // Focused list rows paint inverseSurface, focused buttons paint onSurface; both put inverseOnSurface
    // on top. A light violet with dark text reads well on both and stays on brand.
    inverseSurface = Brand.FocusLight,
    inverseOnSurface = Brand.DarkBackground,
    error = Brand.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Brand.ErrorContainer,
    onErrorContainer = Brand.OnErrorContainer,
    border = Brand.Secondary,
    borderVariant = Brand.DarkOutlineVariant,
    scrim = Color(0xFF000000),
)

/** TV is always dark: easier on the eyes in a living room and closer to TiviMate's look. */
@Composable
fun IptvWhizTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors, content = content)
}
