package com.iptvwhiz.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared design tokens for the TV and mobile themes. Keep in sync with res/values/colors.xml. */
object Brand {
    val Primary = Color(0xFF7C5CFF)
    val PrimaryLight = Color(0xFF5B3FE0)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF22D3EE)
    val OnSecondary = Color(0xFF00252B)
    /** TV focus highlight behind dark text. */
    val FocusLight = Color(0xFFC9BCFF)

    val DarkBackground = Color(0xFF0E1117)
    val DarkSurface = Color(0xFF171B24)
    val DarkSurfaceVariant = Color(0xFF232937)
    val DarkOnSurface = Color(0xFFE8EAF0)
    val DarkOnSurfaceVariant = Color(0xFFA3AABB)

    val LightBackground = Color(0xFFF7F7FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE9E7F5)
    val LightOnSurface = Color(0xFF15171C)
    val LightOnSurfaceVariant = Color(0xFF5B6070)

    val Error = Color(0xFFFF6B6B)
}

/** TV overscan-safe margins (PLAN.md §8). */
object TvSafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}
