package com.iptvwhiz.app.ui.state

import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.WatchlistEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Edits available wherever a channel is shown: live lists, the player, search, watchlists, manage.
 * Works for a channel from any playlist (it carries its own playlist id).
 */
class ChannelEdits(container: AppContainer, private val scope: CoroutineScope) {
    private val overlay = container.overlay
    private val watchlists = container.watchlists

    fun toggleFavorite(channel: Channel) = toggleWatchlist(WatchlistEntity.FAVORITES_ID, channel)

    fun toggleWatchlist(watchlistId: Long, channel: Channel) {
        scope.launch { watchlists.toggle(watchlistId, channel) }
    }

    /** Creates a watchlist and puts the channel in it. [onNameTaken] runs if the name is used. */
    fun addToNewWatchlist(name: String, channel: Channel, onNameTaken: () -> Unit) {
        scope.launch {
            val id = watchlists.create(name)
            if (id == null) onNameTaken() else watchlists.add(id, channel)
        }
    }

    fun setHidden(channel: Channel, hidden: Boolean) {
        scope.launch { overlay.setHidden(channel, hidden) }
    }

    fun rename(channel: Channel, name: String) {
        scope.launch { overlay.rename(channel, name) }
    }

    /** Groups of the channel's own playlist, for "Move to another group". */
    suspend fun groupsFor(channel: Channel): List<ChannelGroup> = overlay.organizedNow(channel.playlistId).groups

    /** Puts the channel at the end of [groupKey] in its playlist. */
    fun moveToGroup(channel: Channel, groupKey: String) {
        if (groupKey == channel.groupTitle) return
        scope.launch {
            val organized = overlay.organizedNow(channel.playlistId)
            val end = (organized.manage(groupKey).maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0
            overlay.moveToGroup(channel, groupKey, end)
        }
    }

    /** Creates a group in the channel's playlist and moves the channel into it. */
    fun moveToNewGroup(channel: Channel, name: String, onNameTaken: () -> Unit) {
        scope.launch {
            val organized = overlay.organizedNow(channel.playlistId)
            val key = overlay.createGroup(channel.playlistId, name, organized.groups)
            if (key == null) onNameTaken() else overlay.moveToGroup(channel, key, 1.0)
        }
    }
}
