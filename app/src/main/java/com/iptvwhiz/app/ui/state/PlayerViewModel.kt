package com.iptvwhiz.app.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.player.LivePlayer
import com.iptvwhiz.app.player.PlaybackProblem
import com.iptvwhiz.app.ui.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plays one channel at a time out of a list: a group of one playlist, a watchlist (which can
 * mix playlists) or a one-off queue such as search results. Lives in a ViewModel so the stream
 * survives rotation on phones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(private val container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val prefs = container.prefs

    private val source: String = savedState[Routes.ARG_SOURCE] ?: Routes.SOURCE_GROUP
    private val playlistId: Long = savedState[Routes.ARG_PLAYLIST] ?: -1L
    /** A group key or [OrganizedPlaylist.ALL] (group source), or a watchlist key. */
    val group: String = savedState[Routes.ARG_GROUP] ?: OrganizedPlaylist.ALL
    private val startKey: String? = savedState[Routes.ARG_KEY]

    val livePlayer = LivePlayer(container.appContext, container.http)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** The list's name: a renamed group or a watchlist; null for "All channels", Favorites and search. */
    private val _groupName = MutableStateFlow<String?>(null)
    val groupName: StateFlow<String?> = _groupName.asStateFlow()

    /** Which kind of list this is, for its title. */
    val sourceKind: String get() = source

    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index.asStateFlow()

    val current: StateFlow<Channel?> = combine(_channels, _index) { list, i -> list.getOrNull(i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What's on the current channel now and next (from that channel's own playlist guide). */
    val programme: StateFlow<NowNext?> = current
        .map { it?.playlistId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyMap()) else container.epg.nowNext(id) }
        .combine(current) { guide, channel -> channel?.let { guide[it.stableKey] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val watchlists: StateFlow<List<Watchlist>> = container.watchlists.watchlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val edits = ChannelEdits(container, viewModelScope)

    init {
        viewModelScope.launch {
            val list = when (source) {
                Routes.SOURCE_WATCHLIST -> {
                    val id = OrganizedPlaylist.watchlistId(group)
                    val watchlist = container.watchlists.current().firstOrNull { it.id == id }
                    _groupName.value = watchlist?.takeUnless { it.isFavorites }?.name
                    watchlist?.available.orEmpty()
                }
                Routes.SOURCE_QUEUE -> container.playQueue
                else -> {
                    val organized = container.overlay.organizedNow(playlistId)
                    _groupName.value = organized.groups.firstOrNull { it.key == group }?.displayName
                    organized.browse(group)
                }
            }
            _channels.value = list
            val start = list.indexOfFirst { it.stableKey == startKey && (playlistId < 0 || it.playlistId == playlistId) }
            playAt(start.coerceAtLeast(0))
        }
        recordHealthFromPlayback()
    }

    /** Playing a channel tells us whether it works, for free (PLAN.md §18). */
    private fun recordHealthFromPlayback() {
        viewModelScope.launch {
            var lastRecorded: Pair<ChannelRef, HealthStatus>? = null
            combine(current, livePlayer.state) { channel, state -> channel to state }.collect { (channel, state) ->
                channel ?: return@collect
                val status = when (state.problem) {
                    PlaybackProblem.Blocked -> HealthStatus.Blocked
                    PlaybackProblem.Offline, PlaybackProblem.Unsupported -> HealthStatus.Offline
                    // No connection is about this device's network, not the channel.
                    PlaybackProblem.NoConnection, PlaybackProblem.Other -> null
                    null -> if (!state.buffering && livePlayer.player.isPlaying) HealthStatus.Working else null
                }
                val entry = status?.let { ChannelRef(channel.playlistId, channel.stableKey) to it }
                if (entry != null && entry != lastRecorded) {
                    lastRecorded = entry
                    container.health.record(channel, entry.second)
                }
            }
        }
    }

    fun playAt(index: Int) {
        val list = _channels.value
        if (list.isEmpty()) return
        val wrapped = index.mod(list.size)
        if (wrapped == _index.value) return
        _index.value = wrapped
        val channel = list[wrapped]
        livePlayer.play(channel)
        prefs.setLastChannel(channel.playlistId, channel.stableKey)
    }

    fun next() = playAt(_index.value + 1)

    fun previous() = playAt(_index.value - 1)

    /** Number entry from the remote. Returns false if no channel has that number in this list. */
    fun playNumber(number: Int): Boolean {
        val i = _channels.value.indexOfFirst { it.number == number }
        if (i < 0) return false
        playAt(i)
        return true
    }

    override fun onCleared() {
        livePlayer.release()
    }
}
