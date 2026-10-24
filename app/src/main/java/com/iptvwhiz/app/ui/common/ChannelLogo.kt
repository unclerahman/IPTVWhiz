package com.iptvwhiz.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

/**
 * Channel logo, or coloured initials when there is no logo or it fails to load (PLAN.md §8).
 * Built on foundation only so it fits both the TV and the mobile theme.
 */
@Composable
fun ChannelLogo(name: String, logoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    // Initials show while the logo loads and if it fails, so a row is never an empty box.
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    var failed by remember(logoUrl) { mutableStateOf(logoUrl.isNullOrBlank()) }
    val shape = RoundedCornerShape(size / 6)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (loaded) LogoTile else colorFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val fontSize = with(LocalDensity.current) { (size * 0.36f).toSp() }
            BasicText(
                text = initials(name),
                style = TextStyle(color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold),
            )
        }
        if (!failed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.size(size * 0.86f),
            )
        }
    }
}

/** Dark tile in both themes: IPTV logos are mostly made for dark backgrounds, and white logos vanish on light ones. */
private val LogoTile = Color(0xFF2A3040)

private val palette = listOf(
    Color(0xFF7C5CFF), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFFEC4899), Color(0xFF6366F1), Color(0xFF14B8A6),
)

private fun colorFor(name: String): Color = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_', '.').filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}
