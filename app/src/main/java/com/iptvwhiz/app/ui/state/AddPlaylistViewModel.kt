package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ImportError
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.PlaylistSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val channelsFound: Int) : ImportState
    data class Done(val playlistId: Long, val channelCount: Int) : ImportState
    data class Failed(val error: ImportError) : ImportState
}

class AddPlaylistViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.playlists
    private val prefs = container.prefs

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var job: Job? = null

    fun submit(name: String, source: PlaylistSource) {
        if (_state.value is ImportState.Running) return
        job = viewModelScope.launch {
            _state.value = ImportState.Running(0)
            var found = 0
            try {
                val id = repo.add(name.trim().ifEmpty { repo.suggestName(source) }, source) { count ->
                    found = count
                    _state.value = ImportState.Running(count)
                }
                // A playlist the user just added is the one they want to watch.
                prefs.setActivePlaylist(id)
                _state.value = ImportState.Done(id, found)
            } catch (e: ImportException) {
                _state.value = ImportState.Failed(e.error)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    fun dismissError() {
        _state.value = ImportState.Idle
    }
}
