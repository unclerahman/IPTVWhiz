package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.ViewingViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea

/** Settings → Viewing options on TV: OK toggles a row. */
@Composable
fun TvViewingScreen() {
    val vm = appViewModel { container, _ -> ViewingViewModel(container) }
    val showQuality by vm.showQuality.collectAsStateWithLifecycle()
    val hideBlocked by vm.hideBlocked.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_viewing), style = MaterialTheme.typography.headlineMedium)
        ToggleRow(
            title = stringResource(R.string.viewing_quality_title),
            description = stringResource(R.string.viewing_quality_desc),
            on = showQuality,
            onToggle = { vm.setShowQuality(!showQuality) },
            modifier = Modifier.focusRequester(first),
        )
        ToggleRow(
            title = stringResource(R.string.viewing_blocked_title),
            description = stringResource(R.string.viewing_blocked_desc),
            on = hideBlocked,
            onToggle = { vm.setHideBlocked(!hideBlocked) },
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = false,
        onClick = onToggle,
        modifier = modifier.widthIn(max = 900.dp),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Text(stringResource(if (on) R.string.toggle_on else R.string.toggle_off)) },
    )
}
