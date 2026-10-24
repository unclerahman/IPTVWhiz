package com.iptvwhiz.app.ui.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.backup.BackupException
import com.iptvwhiz.app.data.backup.RestoreSummary
import com.iptvwhiz.app.ui.common.BackupFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

sealed interface BackupState {
    data object Idle : BackupState
    data object Saving : BackupState
    /** [addingPlaylist] while a playlist from the backup is being downloaded. */
    data class Restoring(val addingPlaylist: String? = null) : BackupState
    data class Saved(val playlists: Int, val location: String?) : BackupState
    data class Restored(val summary: RestoreSummary) : BackupState
    data class Failed(val reason: BackupException.Reason?) : BackupState
}

class BackupViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.backups
    private val context = container.appContext

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _includeLogins = MutableStateFlow(false)
    val includeLogins: StateFlow<Boolean> = _includeLogins.asStateFlow()

    fun setIncludeLogins(include: Boolean) {
        _includeLogins.value = include
    }

    /** Save to a file the user picked. */
    fun saveTo(uri: Uri) = save { context.contentResolver.openOutputStream(uri)?.let { it to null } }

    /** No file picker on this device: save to Download/IPTV Whiz. */
    fun saveToDownloads() = save { BackupFiles.create(context, BackupFiles.suggestedName()) }

    /** [open] returns the stream and, when it's not a picked file, where it was saved. */
    private fun save(open: () -> Pair<OutputStream, String?>?) {
        _state.value = BackupState.Saving
        viewModelScope.launch {
            _state.value = try {
                val (stream, location) = withContext(Dispatchers.IO) { open() }
                    ?: throw BackupException(BackupException.Reason.Unreadable)
                BackupState.Saved(repo.export(stream, _includeLogins.value), location)
            } catch (e: BackupException) {
                BackupState.Failed(e.reason)
            } catch (e: Exception) {
                BackupState.Failed(null)
            }
        }
    }

    fun restoreFrom(uri: Uri) {
        _state.value = BackupState.Restoring()
        viewModelScope.launch {
            _state.value = try {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                    ?: throw BackupException(BackupException.Reason.Unreadable)
                val summary = repo.restore(stream) { name -> _state.value = BackupState.Restoring(name) }
                BackupState.Restored(summary)
            } catch (e: BackupException) {
                BackupState.Failed(e.reason)
            } catch (e: Exception) {
                BackupState.Failed(BackupException.Reason.Unreadable)
            }
        }
    }

    fun dismiss() {
        _state.value = BackupState.Idle
    }
}
