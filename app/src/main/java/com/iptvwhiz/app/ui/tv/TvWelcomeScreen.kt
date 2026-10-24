package com.iptvwhiz.app.ui.tv

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.common.showComingSoon
import com.iptvwhiz.app.ui.theme.TvSafeArea

/**
 * First-run welcome, and also the "add a playlist" chooser later on.
 * [isFirstRun] switches the wording and the bottom button (Explore first / Back).
 */
@Composable
fun TvWelcomeScreen(
    isFirstRun: Boolean,
    onAddM3u: () -> Unit,
    onAddXtream: () -> Unit,
    onSecondary: () -> Unit,
) {
    val context = LocalContext.current
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstCard.requestFocusWhenReady() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(if (isFirstRun) R.string.welcome_title else R.string.add_playlist_title),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (isFirstRun) R.string.welcome_subtitle else R.string.add_playlist_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ProviderCard(
                icon = Icons.Filled.Add,
                title = R.string.add_m3u_title,
                description = R.string.add_m3u_desc,
                onClick = onAddM3u,
                modifier = Modifier.focusRequester(firstCard),
            )
            ProviderCard(
                icon = Icons.Filled.Lock,
                title = R.string.add_xtream_title,
                description = R.string.add_xtream_desc,
                onClick = onAddXtream,
            )
            // TODO(M5): local web page + QR code for typing on the phone (PLAN.md §9).
            ProviderCard(
                icon = Icons.Filled.Phone,
                title = R.string.add_phone_title,
                description = R.string.add_phone_desc,
                onClick = { context.showComingSoon() },
            )
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onSecondary) {
            Text(stringResource(if (isFirstRun) R.string.explore_first else R.string.back))
        }
    }
}

@Composable
private fun ProviderCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.width(260.dp).fillMaxHeight()) {
        Column(Modifier.padding(24.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
