package com.iptvwhiz.app.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.WatchlistEntry
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.ui.Routes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings → Watchlists: create, rename, reorder, delete lists and organise their channels. */
class WatchlistsViewModel(private val container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val repo = container.watchlists

    val watchlists: StateFlow<List<Watchlist>> = repo.watchlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow(savedState.get<String>(Routes.ARG_GROUP)?.toLongOrNull())

    /** The list whose channels are shown; null = the first one. */
    val selected: StateFlow<Long?> = _selected.asStateFlow()

    val entries: StateFlow<List<WatchlistEntry>> = combine(watchlists, _selected) { lists, id ->
        (lists.firstOrNull { it.id == id } ?: lists.firstOrNull())?.entries.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val health: StateFlow<Map<ChannelRef, HealthStatus>> = container.health.statuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val healthCheck = HealthCheck(container, viewModelScope)

    private val _nameTaken = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val nameTaken: SharedFlow<String> = _nameTaken.asSharedFlow()

    fun select(id: Long) {
        _selected.value = id
    }

    fun create(name: String) {
        viewModelScope.launch {
            val id = repo.create(name)
            if (id == null) _nameTaken.emit(name.trim()) else _selected.value = id
        }
    }

    fun rename(list: Watchlist, name: String) {
        viewModelScope.launch { if (!repo.rename(list, name)) _nameTaken.emit(name.trim()) }
    }

    fun delete(list: Watchlist) {
        viewModelScope.launch {
            repo.delete(list)
            if (_selected.value == list.id) _selected.value = null
        }
    }

    fun moveList(ordered: List<Watchlist>, moved: Watchlist) {
        viewModelScope.launch { repo.reorderLists(ordered, moved) }
    }

    fun moveEntry(listId: Long, ordered: List<WatchlistEntry>, moved: WatchlistEntry) {
        viewModelScope.launch { repo.reorderEntries(listId, ordered, moved) }
    }

    fun remove(listId: Long, entry: WatchlistEntry) {
        viewModelScope.launch { repo.remove(listId, entry.ref) }
    }
}
