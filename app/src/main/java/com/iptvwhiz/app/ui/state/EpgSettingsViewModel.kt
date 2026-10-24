package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.db.EpgStatus
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.epg.EpgScheduler
import com.iptvwhiz.app.data.epg.EpgWork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings → TV guide for the active playlist: status, guide links, update now. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgSettingsViewModel(container: AppContainer) : ViewModel() {
    private val context = container.appContext
    private val playlists = container.playlists

    val playlist: StateFlow<PlaylistEntity?> =
        combine(playlists.playlists, container.prefs.activePlaylistId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val playlistId = playlist.map { it?.id }.distinctUntilChanged()

    val status: StateFlow<EpgStatus?> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.epg.status(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val work: StateFlow<EpgWork> = playlistId
        .flatMapLatest { id -> if (id == null) flowOf(EpgWork.Idle) else EpgScheduler.observe(context, id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpgWork.Idle)

    fun updateNow() {
        val id = playlist.value?.id ?: return
        EpgScheduler.updateNow(context, id)
    }

    /** Saves the guide link(s) and loads them straight away. */
    fun saveLinks(links: String) {
        val id = playlist.value?.id ?: return
        viewModelScope.launch {
            playlists.setEpgUrl(id, links.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","))
            EpgScheduler.updateNow(context, id)
        }
    }
}
