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
private val LogoTile = Color(0xFF22223A)

/** Deep, low-saturation tones so a screen full of initials stays calm; white text clears 7:1 on each. */
private val palette = listOf(
    Color(0xFF312E81), Color(0xFF1E3A8A), Color(0xFF065F46), Color(0xFF92400E),
    Color(0xFF991B1B), Color(0xFF831843), Color(0xFF164E63), Color(0xFF4C1D95),
)

private fun colorFor(name: String): Color = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_', '.').filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
    // Playlists often prefix the channel number ("1 TV3"); don't repeat it in the tile when a
    // real word is available.
    val meaningful = words.filter { w -> w.any { it.isLetter() } }.ifEmpty { words }
    return when {
        meaningful.isEmpty() -> "TV"
        meaningful.size == 1 -> meaningful[0].take(2)
        else -> "${meaningful[0].first()}${meaningful[1].first()}"
    }.uppercase()
}
