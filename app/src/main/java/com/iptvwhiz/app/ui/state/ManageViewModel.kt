package com.iptvwhiz.app.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.ui.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ManageEvent {
    data class NameTaken(val name: String) : ManageEvent
}

/** Editing the active playlist: every group and channel, hidden ones included. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageViewModel(container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val overlay = container.overlay

    val activePlaylist: StateFlow<PlaylistEntity?> =
        combine(container.playlists.playlists, container.prefs.activePlaylistId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val organized: StateFlow<OrganizedPlaylist?> = activePlaylist.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else overlay.organized(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<ChannelGroup>> = organized.map { it?.groups.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The group whose channels are shown. Starts from the navigation argument (phone) or the first group. */
    private val _selectedGroup = MutableStateFlow<String?>(savedState[Routes.ARG_GROUP])
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    val channels: StateFlow<List<Channel>> = combine(organized, _selectedGroup) { organized, key ->
        if (organized == null) return@combine emptyList()
        val groupKey = key ?: organized.groups.firstOrNull()?.key ?: return@combine emptyList()
        organized.manage(groupKey)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val edits = ChannelEdits(container, viewModelScope)
    val healthCheck = HealthCheck(container, viewModelScope)

    val health: StateFlow<Map<ChannelRef, HealthStatus>> = container.health.statuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val watchlists: StateFlow<List<Watchlist>> = container.watchlists.watchlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<ManageEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ManageEvent> = _events.asSharedFlow()

    fun selectGroup(key: String) {
        _selectedGroup.value = key
    }

    fun nameTaken(name: String) {
        _events.tryEmit(ManageEvent.NameTaken(name))
    }

    private fun withPlaylist(block: suspend (Long) -> Unit) {
        val id = activePlaylist.value?.id ?: return
        viewModelScope.launch { block(id) }
    }

    fun setGroupHidden(group: ChannelGroup, hidden: Boolean) = withPlaylist { overlay.setGroupHidden(it, group, hidden) }

    fun renameGroup(group: ChannelGroup, name: String) = withPlaylist { overlay.renameGroup(it, group, name) }

    fun createGroup(name: String) = withPlaylist { id ->
        val key = overlay.createGroup(id, name, groups.value)
        if (key == null) nameTaken(name.trim()) else _selectedGroup.value = key
    }

    fun deleteGroup(group: ChannelGroup) = withPlaylist { id ->
        overlay.deleteGroup(id, group)
        if (_selectedGroup.value == group.key) _selectedGroup.value = null
    }

    fun moveGroup(ordered: List<ChannelGroup>, moved: ChannelGroup) = withPlaylist { overlay.reorderGroups(it, ordered, moved) }

    fun moveChannel(ordered: List<Channel>, moved: Channel) {
        viewModelScope.launch { overlay.reorderChannels(ordered, moved) }
    }

    fun sortGroupAlphabetically(group: ChannelGroup) {
        val channels = organized.value?.manage(group.key) ?: return
        viewModelScope.launch { overlay.sortAlphabetically(channels) }
    }

    fun resetGroupOrder(group: ChannelGroup) {
        val channels = organized.value?.manage(group.key) ?: return
        viewModelScope.launch { overlay.resetOrder(channels) }
    }
}
