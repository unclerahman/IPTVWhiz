package com.iptvwhiz.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iptvwhiz.app.R
import com.iptvwhiz.app.player.StreamStats
import java.util.Locale
import kotlin.math.roundToInt

/** "1920×1080 · 25 fps · 4.2 Mbps", shared by the TV and phone players. */
fun StreamStats.summary(): String? = buildList {
    if (height > 0) add("$width×$height")
    if (fps > 0f) add("${fps.roundToInt()} fps")
    if (mbps > 0f) add(String.format(Locale.getDefault(), "%.1f Mbps", mbps))
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Small dark chip over the video. Shows "Measuring…" until the first numbers arrive. */
@Composable
fun QualityBadge(stats: StreamStats, modifier: Modifier = Modifier) {
    Text(
        text = stats.summary() ?: stringResource(R.string.quality_measuring),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .background(Color(0xB3000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
