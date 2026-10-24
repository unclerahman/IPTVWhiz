package com.iptvwhiz.app.ui.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.epg.NowNext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.iptvwhiz.app.player.LivePlayer
import com.iptvwhiz.app.ui.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plays one channel at a time out of a list (the group the user opened it from).
 * Lives in a ViewModel so the stream survives rotation on phones.
 */
class PlayerViewModel(container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val overlay = container.overlay
    private val prefs = container.prefs

    private val playlistId: Long = checkNotNull(savedState[Routes.ARG_PLAYLIST])
    /** A group key, or [OrganizedPlaylist.ALL] / [OrganizedPlaylist.FAVORITES]. */
    val group: String = savedState[Routes.ARG_GROUP] ?: OrganizedPlaylist.ALL
    private val startKey: String? = savedState[Routes.ARG_KEY]

    val livePlayer = LivePlayer(container.appContext, container.http)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** The group's name as the user named it; null for "All channels" and "Favorites". */
    private val _groupName = MutableStateFlow<String?>(null)
    val groupName: StateFlow<String?> = _groupName.asStateFlow()

    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index.asStateFlow()

    val current: StateFlow<Channel?> = combine(_channels, _index) { list, i -> list.getOrNull(i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What's on the current channel now and next. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val programme: StateFlow<NowNext?> = combine(container.epg.nowNext(playlistId), current) { guide, channel ->
        channel?.let { guide[it.stableKey] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val organized = overlay.organizedNow(playlistId)
            val list = organized.browse(group)
            _groupName.value = organized.groups.firstOrNull { it.key == group }?.displayName
            _channels.value = list
            playAt(list.indexOfFirst { it.stableKey == startKey }.coerceAtLeast(0))
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
        prefs.setLastChannel(playlistId, channel.stableKey)
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
