package com.iptvwhiz.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.backup.BackupException
import com.iptvwhiz.app.data.backup.RestoreSummary
import com.iptvwhiz.app.ui.state.BackupViewModel

/** Save/restore actions that use the system file picker when there is one, and Download/IPTV Whiz when not. */
class BackupLaunch(
    val save: () -> Unit,
    val restore: () -> Unit,
    /** Non-null while choosing among backups found without a picker (may be empty). */
    val candidates: List<BackupCandidate>?,
    val dismissCandidates: () -> Unit,
)

@Composable
fun rememberBackupLaunch(vm: BackupViewModel): BackupLaunch {
    val context = LocalContext.current
    var candidates by remember { mutableStateOf<List<BackupCandidate>?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri)
    }
    return BackupLaunch(
        save = {
            try {
                if (context.hasDocumentPicker(Intent.ACTION_CREATE_DOCUMENT)) {
                    saveLauncher.launch(BackupFiles.suggestedName())
                } else {
                    vm.saveToDownloads()
                }
            } catch (e: ActivityNotFoundException) {
                vm.saveToDownloads()
            }
        },
        restore = {
            try {
                if (context.hasDocumentPicker()) {
                    openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } else {
                    candidates = BackupFiles.find(context)
                }
            } catch (e: ActivityNotFoundException) {
                candidates = BackupFiles.find(context)
            }
        },
        candidates = candidates,
        dismissCandidates = { candidates = null },
    )
}

fun Context.backupErrorMessage(reason: BackupException.Reason?): String = getString(
    when (reason) {
        BackupException.Reason.NotABackup -> R.string.backup_error_not_backup
        BackupException.Reason.NewerVersion -> R.string.backup_error_newer
        BackupException.Reason.Unreadable, null -> R.string.backup_error_unreadable
    },
)

fun Context.backupSavedMessage(playlists: Int, location: String?): String {
    val saved = getString(R.string.backup_saved, resources.getQuantityString(R.plurals.playlist_count, playlists, playlists))
    return if (location == null) saved else saved + "\n" + getString(R.string.backup_saved_to, location)
}

fun Context.restoreSummaryMessage(summary: RestoreSummary): String = buildList {
    fun playlists(n: Int) = resources.getQuantityString(R.plurals.playlist_count, n, n)
    if (summary.merged > 0) add(getString(R.string.backup_merged, playlists(summary.merged)))
    if (summary.added > 0) add(getString(R.string.backup_added, playlists(summary.added)))
    if (summary.skipped > 0) add(getString(R.string.backup_skipped, playlists(summary.skipped)))
    if (summary.failed > 0) add(getString(R.string.backup_failed_count, playlists(summary.failed)))
    add(
        if (summary.changes > 0) getString(R.string.backup_changes, summary.changes)
        else getString(R.string.backup_no_changes),
    )
}.joinToString("\n")
