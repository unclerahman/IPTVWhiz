package com.iptvwhiz.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnSecondary,
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
    border = Brand.Secondary,
    error = Brand.Error,
)

/** TV is always dark: easier on the eyes in a living room and closer to TiviMate's look. */
@Composable
fun IptvWhizTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors, content = content)
}
