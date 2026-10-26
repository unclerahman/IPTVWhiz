package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.epg.NowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/** Browsing: the active playlist's groups, plus the user's watchlists (which span playlists). */
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

    val watchlists: StateFlow<List<Watchlist>> = container.watchlists.watchlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Channels in Favorites, to mark them in any list. */
    val favorites: StateFlow<Set<ChannelRef>> = watchlists
        .map { lists -> lists.firstOrNull { it.isFavorites }?.entries?.map { it.ref }?.toSet().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _selectedGroup = MutableStateFlow(OrganizedPlaylist.ALL)

    /** A group key, [OrganizedPlaylist.ALL], or a watchlist key ([OrganizedPlaylist.watchlistKey]). */
    val selectedGroup: StateFlow<String> = _selectedGroup.asStateFlow()

    val groups: StateFlow<List<ChannelGroup>> = organized.map { it?.visibleGroups.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<Channel>> = combine(organized, watchlists, _selectedGroup) { organized, lists, key ->
        val watchlistId = OrganizedPlaylist.watchlistId(key)
        if (watchlistId != null) {
            lists.firstOrNull { it.id == watchlistId }?.available.orEmpty()
        } else {
            organized?.browse(key).orEmpty()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val edits = ChannelEdits(container, viewModelScope)

    /** What's on now/next for every playlist's channels (watchlists mix playlists). */
    val guide: StateFlow<Map<ChannelRef, NowNext>> = repo.playlists
        .map { list -> list.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> mergedGuide(container, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val health: StateFlow<Map<ChannelRef, HealthStatus>> = container.health.statuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // A different playlist starts at "All channels".
        playlistId.onEach { _selectedGroup.value = OrganizedPlaylist.ALL }.launchIn(viewModelScope)
    }

    fun selectGroup(key: String) {
        _selectedGroup.value = key
    }
}

/** Now/next of several playlists in one map. */
fun mergedGuide(container: AppContainer, playlistIds: List<Long>): Flow<Map<ChannelRef, NowNext>> {
    if (playlistIds.isEmpty()) return flowOf(emptyMap())
    val flows = playlistIds.map { id ->
        container.epg.nowNext(id).map { guide -> guide.mapKeys { (key, _) -> ChannelRef(id, key) } }
    }
    return combine(flows) { maps -> maps.fold(emptyMap<ChannelRef, NowNext>()) { acc, m -> acc + m } }
}
