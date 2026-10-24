package com.iptvwhiz.app.ui.mobile

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.backupErrorMessage
import com.iptvwhiz.app.ui.common.backupSavedMessage
import com.iptvwhiz.app.ui.common.rememberBackupLaunch
import com.iptvwhiz.app.ui.common.restoreSummaryMessage
import com.iptvwhiz.app.ui.state.BackupState
import com.iptvwhiz.app.ui.state.BackupViewModel
import com.iptvwhiz.app.ui.state.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBackupScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> BackupViewModel(container) }
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val includeLogins by vm.includeLogins.collectAsStateWithLifecycle()
    val launch = rememberBackupLaunch(vm)
    val busy = state == BackupState.Saving || state is BackupState.Restoring

    Scaffold(topBar = { BackTopBar(stringResource(R.string.backup_title), onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.backup_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_include_logins)) },
                supportingContent = { Text(stringResource(R.string.backup_include_logins_desc)) },
                trailingContent = { Switch(checked = includeLogins, onCheckedChange = vm::setIncludeLogins) },
                modifier = Modifier.clickable { vm.setIncludeLogins(!includeLogins) },
            )
            Button(onClick = launch.save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_save))
            }
            OutlinedButton(onClick = launch.restore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore))
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (val current = state) {
                            is BackupState.Restoring -> current.addingPlaylist?.let { stringResource(R.string.backup_adding, it) }
                                ?: stringResource(R.string.backup_restoring)
                            else -> stringResource(R.string.backup_saving)
                        },
                    )
                }
            }
        }
    }

    val result: Pair<String, String>? = when (val current = state) {
        is BackupState.Saved -> stringResource(R.string.backup_title) to context.backupSavedMessage(current.playlists, current.location)
        is BackupState.Restored -> stringResource(R.string.backup_restored_title) to context.restoreSummaryMessage(current.summary)
        is BackupState.Failed -> stringResource(R.string.backup_error_title) to context.backupErrorMessage(current.reason)
        else -> null
    }
    result?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } },
        )
    }

    launch.candidates?.let { candidates ->
        AlertDialog(
            onDismissRequest = launch.dismissCandidates,
            title = { Text(stringResource(R.string.backup_choose)) },
            text = {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.backup_none_found))
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(candidates, key = { it.uri.toString() }) { candidate ->
                            ListItem(
                                headlineContent = { Text(candidate.name) },
                                supportingContent = {
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(
                                            candidate.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                        ).toString(),
                                    )
                                },
                                modifier = Modifier.clickable {
                                    launch.dismissCandidates()
                                    vm.restoreFrom(candidate.uri)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = launch.dismissCandidates) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
