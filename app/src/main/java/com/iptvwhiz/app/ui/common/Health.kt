package com.iptvwhiz.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.HealthStatus

/** Green = working, red = offline, amber = blocked. Nothing when the channel hasn't been tried. */
@Composable
fun HealthDot(status: HealthStatus?, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    status ?: return
    val (color, label) = when (status) {
        HealthStatus.Working -> Color(0xFF22C55E) to R.string.health_working
        HealthStatus.Offline -> Color(0xFFEF4444) to R.string.health_offline
        HealthStatus.Blocked -> Color(0xFFF59E0B) to R.string.health_blocked
    }
    val description = stringResource(label)
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .semantics { contentDescription = description },
    )
}
