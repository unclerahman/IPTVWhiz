package com.iptvwhiz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
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
    surfaceContainer = Brand.DarkSurface,
    error = Brand.Error,
)

private val LightColors = lightColorScheme(
    primary = Brand.PrimaryLight,
    onPrimary = Brand.OnPrimary,
    secondary = Brand.Secondary,
    onSecondary = Brand.OnSecondary,
    background = Brand.LightBackground,
    onBackground = Brand.LightOnSurface,
    surface = Brand.LightSurface,
    onSurface = Brand.LightOnSurface,
    surfaceVariant = Brand.LightSurfaceVariant,
    onSurfaceVariant = Brand.LightOnSurfaceVariant,
    surfaceContainer = Brand.LightSurfaceVariant,
)

@Composable
fun IptvWhizMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
