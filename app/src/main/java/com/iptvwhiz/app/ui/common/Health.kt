package com.iptvwhiz.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.HealthStatus

/** Green = working, red = offline, amber = blocked. Nothing when the channel hasn't been tried. */
@Composable
fun HealthDot(status: HealthStatus?, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    status ?: return
    val (color, label) = status.colorAndLabel()
    val description = stringResource(label)
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .semantics { contentDescription = description },
    )
}

/**
 * Dot plus label, for channel rows where the reason a channel is dim should be readable at a
 * glance. Only channels that need attention get one: a green dot on every checked channel is
 * noise, so [HealthStatus.Working] draws nothing.
 */
@Composable
fun HealthBadge(status: HealthStatus?, modifier: Modifier = Modifier) {
    if (status == null || status == HealthStatus.Working) return
    val (color, label) = status.colorAndLabel()
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun HealthStatus.colorAndLabel(): Pair<Color, Int> = when (this) {
    HealthStatus.Working -> Color(0xFF22C55E) to R.string.health_working
    HealthStatus.Offline -> Color(0xFFEF4444) to R.string.health_offline
    HealthStatus.Blocked -> Color(0xFFF59E0B) to R.string.health_blocked
}
