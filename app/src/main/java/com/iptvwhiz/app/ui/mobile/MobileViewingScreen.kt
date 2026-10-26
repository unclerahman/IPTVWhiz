package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.state.ViewingViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Settings → Viewing options on phones. */
@Composable
fun MobileViewingScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> ViewingViewModel(container) }
    val showQuality by vm.showQuality.collectAsStateWithLifecycle()
    val hideBlocked by vm.hideBlocked.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_viewing), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ToggleRow(
                title = stringResource(R.string.viewing_quality_title),
                description = stringResource(R.string.viewing_quality_desc),
                on = showQuality,
                onChange = vm::setShowQuality,
            )
            HorizontalDivider()
            ToggleRow(
                title = stringResource(R.string.viewing_blocked_title),
                description = stringResource(R.string.viewing_blocked_desc),
                on = hideBlocked,
                onChange = vm::setHideBlocked,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!on) },
    )
}
