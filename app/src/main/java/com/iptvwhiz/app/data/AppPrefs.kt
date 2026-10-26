package com.iptvwhiz.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app state that doesn't belong in the database. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("iptvwhiz", Context.MODE_PRIVATE)

    private val _activePlaylistId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_PLAYLIST, NONE))
    val activePlaylistId: StateFlow<Long> = _activePlaylistId.asStateFlow()

    fun setActivePlaylist(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_PLAYLIST, id).apply()
        _activePlaylistId.value = id
    }

    fun lastChannelKey(playlistId: Long): String? = prefs.getString("$KEY_LAST_CHANNEL$playlistId", null)

    fun setLastChannel(playlistId: Long, stableKey: String) {
        prefs.edit().putString("$KEY_LAST_CHANNEL$playlistId", stableKey).apply()
    }

    private val _showQuality = MutableStateFlow(prefs.getBoolean(KEY_SHOW_QUALITY, false))

    /** Show resolution, FPS and Mbps over the video. */
    val showQuality: StateFlow<Boolean> = _showQuality.asStateFlow()

    fun setShowQuality(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_QUALITY, on).apply()
        _showQuality.value = on
    }

    private val _hideBlocked = MutableStateFlow(prefs.getBoolean(KEY_HIDE_BLOCKED, false))

    /** Leave channels the last health check found blocked out of Live TV, search and zapping. */
    val hideBlocked: StateFlow<Boolean> = _hideBlocked.asStateFlow()

    fun setHideBlocked(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_BLOCKED, on).apply()
        _hideBlocked.value = on
    }

    companion object {
        const val NONE = -1L
        private const val KEY_SHOW_QUALITY = "show_quality"
        private const val KEY_HIDE_BLOCKED = "hide_blocked"
        private const val KEY_ACTIVE_PLAYLIST = "active_playlist"
        private const val KEY_LAST_CHANNEL = "last_channel_"
    }
}
