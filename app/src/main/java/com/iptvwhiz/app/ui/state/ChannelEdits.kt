package com.iptvwhiz.app.ui.state

import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.OverlayRepository
import com.iptvwhiz.app.data.db.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Edits available wherever a channel is shown (live list long-press, manage screen). */
class ChannelEdits(private val overlay: OverlayRepository, private val scope: CoroutineScope) {

    fun toggleFavorite(channel: Channel) {
        scope.launch { overlay.setFavorite(channel, !channel.favorite) }
    }

    fun setHidden(channel: Channel, hidden: Boolean) {
        scope.launch { overlay.setHidden(channel, hidden) }
    }

    fun rename(channel: Channel, name: String) {
        scope.launch { overlay.rename(channel, name) }
    }

    /** Puts the channel at the end of [groupKey]. */
    fun moveToGroup(channel: Channel, groupKey: String, organized: OrganizedPlaylist) {
        if (groupKey == channel.groupTitle) return
        val end = (organized.manage(groupKey).maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0
        scope.launch { overlay.moveToGroup(channel, groupKey, end) }
    }

    /** Creates a group and moves the channel into it. [onNameTaken] runs if a group already has that name. */
    fun moveToNewGroup(channel: Channel, name: String, organized: OrganizedPlaylist, onNameTaken: () -> Unit) {
        scope.launch {
            val key = overlay.createGroup(channel.playlistId, name, organized.groups)
            if (key == null) onNameTaken() else overlay.moveToGroup(channel, key, 1.0)
        }
    }
}
