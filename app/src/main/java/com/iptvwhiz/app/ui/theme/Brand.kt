package com.iptvwhiz.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared design tokens for the TV and mobile themes. Keep in sync with res/values/colors.xml.
 *
 * Palette: the "Dark Mode (OLED)" design system — midnight indigo brand hues, deep navy surfaces
 * and a green live accent. The dark brand tones (#1E1B4B / #4338CA / #312E81) fill buttons and
 * containers; the light tones (#A5B4FC / #C7D2FE) carry text and icons on the dark background so
 * every pair stays above 4.5:1.
 */
object Brand {
    // Brand hues.
    val IndigoDeep = Color(0xFF1E1B4B)
    val Indigo = Color(0xFF4338CA)
    val IndigoBorder = Color(0xFF312E81)
    val IndigoSoft = Color(0xFFA5B4FC)
    val IndigoPale = Color(0xFFE0E7FF)

    /** On-air green: live badges and working channels. */
    val Live = Color(0xFF22C55E)
    val LiveDeep = Color(0xFF14532D)
    val LivePale = Color(0xFFBBF7D0)

    val Primary = IndigoSoft
    val PrimaryLight = Indigo
    val OnPrimary = IndigoDeep
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val Secondary = IndigoPale
    val OnSecondary = IndigoDeep
    /** TV focus highlight behind dark text. */
    val FocusLight = IndigoPale

    val DarkBackground = Color(0xFF0F0F23)
    val DarkSurface = Color(0xFF1B1B30)
    val DarkSurfaceContainerLow = Color(0xFF14142A)
    val DarkSurfaceContainerHigh = Color(0xFF22223A)
    val DarkSurfaceContainerHighest = Color(0xFF2A2A44)
    val DarkSurfaceVariant = Color(0xFF27273B)
    val DarkOnSurface = Color(0xFFF8FAFC)
    val DarkOnSurfaceVariant = Color(0xFF94A3B8)
    val DarkOutline = IndigoBorder
    val DarkOutlineVariant = Color(0xFF27273B)

    val LightBackground = Color(0xFFF7F7FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE9E7F5)
    val LightOnSurface = Color(0xFF15171C)
    val LightOnSurfaceVariant = Color(0xFF5B6070)

    val Error = Color(0xFFEF4444)
    val ErrorContainer = Color(0xFF7F1D1D)
    val OnErrorContainer = Color(0xFFFEE2E2)
}

/** TV overscan-safe margins (PLAN.md §8). */
object TvSafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}
