package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.db.ChannelEntity
import com.iptvwhiz.app.data.db.GroupInfo
import com.iptvwhiz.app.data.db.PlaylistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Browsing the active playlist: its groups and the channels of the selected group. */
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

    private val _selectedGroup = MutableStateFlow<String?>(null)

    /** null = all channels. */
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    val groups: StateFlow<List<GroupInfo>> = activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.groups(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<ChannelEntity>> =
        combine(activePlaylist.map { it?.id }.distinctUntilChanged(), _selectedGroup) { id, group -> id to group }
            .flatMapLatest { (id, group) -> if (id == null) flowOf(emptyList()) else repo.channels(id, group) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // A different playlist starts at "All channels".
        activePlaylist.map { it?.id }.distinctUntilChanged()
            .onEach { _selectedGroup.value = null }
            .launchIn(viewModelScope)
    }

    fun selectGroup(name: String?) {
        _selectedGroup.value = name
    }
}
