package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.ChannelSearch
import com.iptvwhiz.app.data.SearchFilters
import com.iptvwhiz.app.data.blockedRefs
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.effectiveCountry
import com.iptvwhiz.app.data.epg.NowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Search across every playlist by name, country, group and playlist (PLAN.md §18). */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val visibleChannels = combine(
        container.db.watchlists().observeAllChannels(),
        container.overlay.hiddenGroups(),
        container.health.statuses(),
        container.prefs.hideBlocked,
    ) { channels, hiddenGroups, health, hideBlocked ->
        val blocked = if (hideBlocked) health.blockedRefs() else emptySet()
        channels.filter { !it.hidden && (it.playlistId to it.groupTitle) !in hiddenGroups && it.ref !in blocked }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    val results: StateFlow<List<Channel>> = combine(visibleChannels, _filters.debounce(250)) { channels, filters ->
        ChannelSearch.search(channels, emptySet(), filters)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Countries to choose from (code to channel count), narrowed by the chosen playlist. */
    val countries: StateFlow<List<Pair<String, Int>>> = combine(visibleChannels, _filters) { channels, filters ->
        ChannelSearch.countries(channels.filter { filters.playlistId == null || it.playlistId == filters.playlistId })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Groups to choose from, narrowed by the chosen playlist and country. */
    val groups: StateFlow<List<Pair<String, Int>>> = combine(visibleChannels, _filters) { channels, filters ->
        ChannelSearch.groups(
            channels.filter {
                (filters.playlistId == null || it.playlistId == filters.playlistId) &&
                    (filters.country == null || it.effectiveCountry == filters.country)
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = container.playlists.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val guide: StateFlow<Map<ChannelRef, NowNext>> = container.playlists.playlists
        .map { list -> list.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> mergedGuide(container, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val health: StateFlow<Map<ChannelRef, HealthStatus>> = container.health.statuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val watchlists: StateFlow<List<Watchlist>> = container.watchlists.watchlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<Set<ChannelRef>> = watchlists
        .map { lists -> lists.firstOrNull { it.isFavorites }?.entries?.map { it.ref }?.toSet().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val edits = ChannelEdits(container, viewModelScope)
    val healthCheck = HealthCheck(container, viewModelScope)

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }

    /** Changing the country clears a group that no longer fits. */
    fun setCountry(code: String?) = _filters.update { it.copy(country = code, group = null) }

    fun setGroup(group: String?) = _filters.update { it.copy(group = group) }

    fun setPlaylist(id: Long?) = _filters.update { it.copy(playlistId = id, country = null, group = null) }

    fun clear() {
        _filters.value = SearchFilters()
    }

    /** Before playing a result: zapping moves through the results. */
    fun useResultsAsQueue() {
        container.playQueue = results.value
    }
}
