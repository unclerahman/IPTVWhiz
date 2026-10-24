package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ImportError
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.db.PlaylistEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistEvent {
    data class Refreshed(val name: String) : PlaylistEvent
    data class RefreshFailed(val name: String, val error: ImportError) : PlaylistEvent
    data class Deleted(val name: String) : PlaylistEvent
}

class PlaylistsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.playlists
    private val prefs = container.prefs

    val playlists: StateFlow<List<PlaylistEntity>?> =
        repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeId: StateFlow<Long> = prefs.activePlaylistId

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlaylistEvent> = _events.asSharedFlow()

    fun setActive(playlist: PlaylistEntity) = prefs.setActivePlaylist(playlist.id)

    fun refresh(playlist: PlaylistEntity) {
        if (playlist.id in _refreshing.value) return
        _refreshing.update { it + playlist.id }
        viewModelScope.launch {
            try {
                repo.refresh(playlist.id)
                _events.emit(PlaylistEvent.Refreshed(playlist.name))
            } catch (e: ImportException) {
                _events.emit(PlaylistEvent.RefreshFailed(playlist.name, e.error))
            } finally {
                _refreshing.update { it - playlist.id }
            }
        }
    }

    fun delete(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repo.delete(playlist.id)
            _events.emit(PlaylistEvent.Deleted(playlist.name))
        }
    }
}
