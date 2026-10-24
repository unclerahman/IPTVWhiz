package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.epg.NowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Browsing the active playlist: favorites, all channels, and the user's visible groups. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.playlists

    /** null while loading. */
    val playlists: StateFlow<List<PlaylistEntity>?> =
        repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activePlaylist: StateFlow<PlaylistEntity?> =
        combine(repo.playlists, container.prefs.activePlaylistId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val playlistId = activePlaylist.map { it?.id }.distinctUntilChanged()

    val organized: StateFlow<OrganizedPlaylist?> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.overlay.organized(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedGroup = MutableStateFlow(OrganizedPlaylist.ALL)

    /** A group key, or [OrganizedPlaylist.ALL] / [OrganizedPlaylist.FAVORITES]. */
    val selectedGroup: StateFlow<String> = _selectedGroup.asStateFlow()

    val groups: StateFlow<List<ChannelGroup>> = organized.map { it?.visibleGroups.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<Channel>> = combine(organized, _selectedGroup) { organized, key ->
        organized?.browse(key).orEmpty()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val edits = ChannelEdits(container.overlay, viewModelScope)

    /** What's on now/next, by channel stable key. Empty until a guide is loaded. */
    val nowNext: StateFlow<Map<String, NowNext>> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyMap()) else container.epg.nowNext(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // A different playlist starts at "All channels".
        playlistId.onEach { _selectedGroup.value = OrganizedPlaylist.ALL }.launchIn(viewModelScope)
    }

    fun selectGroup(key: String) {
        _selectedGroup.value = key
    }
}
