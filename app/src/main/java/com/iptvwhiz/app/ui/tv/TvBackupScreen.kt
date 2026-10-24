package com.iptvwhiz.app.ui.tv

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.common.backupErrorMessage
import com.iptvwhiz.app.ui.common.backupSavedMessage
import com.iptvwhiz.app.ui.common.rememberBackupLaunch
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.common.restoreSummaryMessage
import com.iptvwhiz.app.ui.state.BackupState
import com.iptvwhiz.app.ui.state.BackupViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea

@Composable
fun TvBackupScreen() {
    val vm = appViewModel { container, _ -> BackupViewModel(container) }
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val includeLogins by vm.includeLogins.collectAsStateWithLifecycle()
    val launch = rememberBackupLaunch(vm)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenReady() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.backup_intro),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(820.dp),
        )
        ListItem(
            selected = includeLogins,
            onClick = { vm.setIncludeLogins(!includeLogins) },
            modifier = Modifier.width(820.dp),
            headlineContent = { Text(stringResource(R.string.backup_include_logins)) },
            supportingContent = { Text(stringResource(R.string.backup_include_logins_desc)) },
            trailingContent = {
                Text(stringResource(if (includeLogins) R.string.label_on else R.string.label_off))
            },
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = launch.save, modifier = Modifier.focusRequester(firstFocus)) {
            Text(stringResource(R.string.backup_save))
        }
        Button(onClick = launch.restore) { Text(stringResource(R.string.backup_restore)) }

        when (val current = state) {
            BackupState.Saving -> Busy(stringResource(R.string.backup_saving))
            is BackupState.Restoring -> Busy(
                current.addingPlaylist?.let { stringResource(R.string.backup_adding, it) }
                    ?: stringResource(R.string.backup_restoring),
            )
            else -> Unit
        }
    }

    when (val current = state) {
        is BackupState.Saved -> ResultDialog(
            title = stringResource(R.string.backup_title),
            message = context.backupSavedMessage(current.playlists, current.location),
            onDismiss = vm::dismiss,
        )
        is BackupState.Restored -> ResultDialog(
            title = stringResource(R.string.backup_restored_title),
            message = context.restoreSummaryMessage(current.summary),
            onDismiss = vm::dismiss,
        )
        is BackupState.Failed -> ResultDialog(
            title = stringResource(R.string.backup_error_title),
            message = context.backupErrorMessage(current.reason),
            onDismiss = vm::dismiss,
        )
        else -> Unit
    }

    launch.candidates?.let { candidates ->
        if (candidates.isEmpty()) {
            ResultDialog(
                title = stringResource(R.string.backup_choose),
                message = stringResource(R.string.backup_none_found),
                onDismiss = launch.dismissCandidates,
            )
        } else {
            val first = remember { FocusRequester() }
            TvDialog(title = stringResource(R.string.backup_choose), onDismiss = launch.dismissCandidates) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.uri.toString() }) { candidate ->
                        ListItem(
                            selected = false,
                            onClick = {
                                launch.dismissCandidates()
                                vm.restoreFrom(candidate.uri)
                            },
                            modifier = if (candidate == candidates.first()) Modifier.focusRequester(first) else Modifier,
                            headlineContent = { Text(candidate.name) },
                            supportingContent = {
                                Text(
                                    DateUtils.getRelativeTimeSpanString(
                                        candidate.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                    ).toString(),
                                )
                            },
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { first.requestFocusWhenReady() }
        }
    }
}

@Composable
private fun Busy(text: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.secondary,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    val ok = remember { FocusRequester() }
    TvDialog(title = title, message = message, onDismiss = onDismiss) {
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().focusRequester(ok)) {
            Text(stringResource(R.string.ok))
        }
    }
    LaunchedEffect(Unit) { ok.requestFocusWhenReady() }
}
