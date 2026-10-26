package com.iptvwhiz.app.data

import androidx.room.withTransaction
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.ChannelOverride
import com.iptvwhiz.app.data.db.GroupSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The user's edits: hide, rename, favorite, regroup and reorder channels and groups. */
class OverlayRepository(private val db: AppDatabase) {
    private val dao = db.overlay()

    fun organized(playlistId: Long): Flow<OrganizedPlaylist> =
        combine(dao.observeChannels(playlistId), dao.observeGroupSettings(playlistId)) { channels, settings ->
            OrganizedPlaylist.build(channels, settings)
        }.flowOn(Dispatchers.Default)

    suspend fun organizedNow(playlistId: Long): OrganizedPlaylist =
        OrganizedPlaylist.build(dao.channels(playlistId), dao.groupSettings(playlistId))

    /** Hidden groups of every playlist, as (playlistId, group key). */
    fun hiddenGroups(): Flow<Set<Pair<Long, String>>> =
        dao.observeHiddenGroups().map { rows -> rows.map { it.playlistId to it.name }.toSet() }

    // Channels

    suspend fun setHidden(channel: Channel, hidden: Boolean) = editChannel(channel) { it.copy(hidden = hidden) }

    /** Hides or shows many channels at once (e.g. every offline channel after a check). */
    suspend fun setHiddenAll(channels: List<Channel>, hidden: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            channels.groupBy { it.playlistId }.forEach { (playlistId, inPlaylist) ->
                inPlaylist.chunked(QUERY_CHUNK).forEach { chunk ->
                    val existing = dao.overrides(playlistId, chunk.map { it.stableKey }).associateBy { it.stableKey }
                    dao.upsertAll(
                        chunk.map { channel ->
                            (existing[channel.stableKey] ?: ChannelOverride(playlistId, channel.stableKey))
                                .copy(hidden = hidden, updatedAt = now)
                        },
                    )
                }
            }
        }
    }

    /** Blank or the original name resets to the provider's name. */
    suspend fun rename(channel: Channel, name: String) = editChannel(channel) {
        val trimmed = name.trim()
        it.copy(customName = trimmed.takeUnless { n -> n.isEmpty() || n == channel.originalName })
    }

    /** Moves the channel to the end of [groupKey]. Its own provider group clears the custom group. */
    suspend fun moveToGroup(channel: Channel, groupKey: String, endPosition: Double) = editChannel(channel) {
        it.copy(customGroup = groupKey.takeUnless { k -> k == channel.originalGroup }, sortOrder = endPosition)
    }

    /**
     * [ordered] is the group after the user moved [moved] to its new place. Writes one row,
     * or renumbers the whole group when positions have become too close.
     */
    suspend fun reorderChannels(ordered: List<Channel>, moved: Channel) {
        val index = ordered.indexOfFirst { it.stableKey == moved.stableKey }
        if (index < 0) return
        val position = Positions.between(
            ordered.getOrNull(index - 1)?.sortOrder,
            ordered.getOrNull(index + 1)?.sortOrder,
        )
        if (position != null) {
            editChannel(moved) { it.copy(sortOrder = position) }
        } else {
            setChannelPositions(ordered.mapIndexed { i, ch -> ch to (i + 1).toDouble() })
        }
    }

    suspend fun sortAlphabetically(channels: List<Channel>) =
        setChannelPositions(channels.sortedBy { it.name.lowercase() }.mapIndexed { i, ch -> ch to (i + 1).toDouble() })

    /** Back to the provider's order. */
    suspend fun resetOrder(channels: List<Channel>) = setChannelPositions(channels.map { it to null })

    private suspend fun setChannelPositions(positions: List<Pair<Channel, Double?>>) {
        if (positions.isEmpty()) return
        val playlistId = positions.first().first.playlistId
        val now = System.currentTimeMillis()
        db.withTransaction {
            positions.chunked(QUERY_CHUNK).forEach { chunk ->
                val existing = dao.overrides(playlistId, chunk.map { it.first.stableKey }).associateBy { it.stableKey }
                dao.upsertAll(
                    chunk.map { (channel, position) ->
                        (existing[channel.stableKey] ?: ChannelOverride(playlistId, channel.stableKey))
                            .copy(sortOrder = position, updatedAt = now)
                    },
                )
            }
        }
    }

    private suspend fun editChannel(channel: Channel, change: (ChannelOverride) -> ChannelOverride) {
        db.withTransaction {
            val current = dao.override(channel.playlistId, channel.stableKey)
                ?: ChannelOverride(channel.playlistId, channel.stableKey)
            dao.upsert(change(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // Groups

    suspend fun setGroupHidden(playlistId: Long, group: ChannelGroup, hidden: Boolean) =
        editGroup(playlistId, group) { it.copy(hidden = hidden) }

    /** Blank or the original name resets a provider group's name. */
    suspend fun renameGroup(playlistId: Long, group: ChannelGroup, name: String) = editGroup(playlistId, group) {
        val trimmed = name.trim()
        it.copy(customName = trimmed.takeUnless { n -> n.isEmpty() || n == group.key })
    }

    /** Creates a custom group at the end and returns its key, or null if the name is taken. */
    suspend fun createGroup(playlistId: Long, name: String, existing: List<ChannelGroup>): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || existing.any { it.key == trimmed || it.displayName.equals(trimmed, ignoreCase = true) }) {
            return null
        }
        val position = (existing.maxOfOrNull { it.sortOrder }?.takeIf { it < Double.MAX_VALUE } ?: 0.0) + 1.0
        dao.upsertGroup(GroupSetting(playlistId, trimmed, sortOrder = position, isCustom = true))
        return trimmed
    }

    /** Only custom groups can be deleted; their channels return to where the provider put them. */
    suspend fun deleteGroup(playlistId: Long, group: ChannelGroup) {
        if (!group.isCustom) return
        db.withTransaction {
            dao.releaseGroup(playlistId, group.key, System.currentTimeMillis())
            dao.deleteGroup(playlistId, group.key)
        }
    }

    suspend fun reorderGroups(playlistId: Long, ordered: List<ChannelGroup>, moved: ChannelGroup) {
        val index = ordered.indexOfFirst { it.key == moved.key }
        if (index < 0) return
        val position = Positions.between(
            ordered.getOrNull(index - 1)?.sortOrder,
            ordered.getOrNull(index + 1)?.sortOrder,
        )
        if (position != null) {
            editGroup(playlistId, moved) { it.copy(sortOrder = position) }
        } else {
            val now = System.currentTimeMillis()
            db.withTransaction {
                val existing = dao.groupSettings(playlistId).associateBy { it.name }
                dao.upsertGroups(
                    ordered.mapIndexed { i, group ->
                        (existing[group.key] ?: GroupSetting(playlistId, group.key))
                            .copy(sortOrder = (i + 1).toDouble(), updatedAt = now)
                    },
                )
            }
        }
    }

    private suspend fun editGroup(playlistId: Long, group: ChannelGroup, change: (GroupSetting) -> GroupSetting) {
        db.withTransaction {
            val current = dao.groupSettings(playlistId).firstOrNull { it.name == group.key }
                ?: GroupSetting(playlistId, group.key, isCustom = group.isCustom)
            dao.upsertGroup(change(current).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private companion object {
        /** SQLite allows 999 parameters per query. */
        const val QUERY_CHUNK = 500
    }
}
