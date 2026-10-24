package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.epg.EpgRepository
import com.iptvwhiz.app.data.epg.EpgWork
import com.iptvwhiz.app.ui.common.epgStatusMessage
import com.iptvwhiz.app.ui.state.EpgSettingsViewModel
import com.iptvwhiz.app.ui.state.appViewModel

@Composable
fun MobileEpgSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> EpgSettingsViewModel(container) }
    val context = LocalContext.current
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val work by vm.work.collectAsStateWithLifecycle()
    var links by remember(playlist?.id) { mutableStateOf(playlist?.epgUrl.orEmpty().replace(",", ",\n")) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_epg), onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = playlist
            if (current == null) {
                Text(stringResource(R.string.manage_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(stringResource(R.string.guide_settings_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${current.name}: " + context.epgStatusMessage(status, work, EpgRepository.links(current.epgUrl).size),
                style = MaterialTheme.typography.titleSmall,
                color = if (work is EpgWork.Running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = vm::updateNow, enabled = work !is EpgWork.Running, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guide_update_now))
            }
            OutlinedTextField(
                value = links,
                onValueChange = { links = it },
                label = { Text(stringResource(R.string.guide_links)) },
                supportingText = { Text(stringResource(R.string.guide_links_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { vm.saveLinks(links) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guide_save_link))
            }
        }
    }
}
