package com.iptvwhiz.app.data

import androidx.room.withTransaction
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.WatchlistEntity
import com.iptvwhiz.app.data.db.WatchlistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** A channel's identity across playlists. */
data class ChannelRef(val playlistId: Long, val stableKey: String)

val Channel.ref: ChannelRef get() = ChannelRef(playlistId, stableKey)

/** One watchlist entry; [channel] is null while the channel is missing from its playlist. */
data class WatchlistEntry(
    val item: WatchlistItem,
    val channel: Channel?,
    val playlistName: String,
) {
    val ref get() = ChannelRef(item.playlistId, item.stableKey)
}

data class Watchlist(
    val id: Long,
    val name: String,
    val builtIn: Boolean,
    val position: Double,
    val entries: List<WatchlistEntry>,
) {
    val isFavorites get() = id == WatchlistEntity.FAVORITES_ID

    /** Channels that can be played right now, in watchlist order. */
    val available: List<Channel> get() = entries.mapNotNull { it.channel }

    fun contains(ref: ChannelRef) = entries.any { it.ref == ref }
}

object Watchlists {
    /** Joins lists, items and channels. Pure, so it can be tested without a database. */
    fun build(
        lists: List<WatchlistEntity>,
        items: List<WatchlistItem>,
        channels: List<Channel>,
        playlists: List<PlaylistEntity>,
    ): List<Watchlist> {
        val byRef = channels.associateBy { it.ref }
        val names = playlists.associate { it.id to it.name }
        val itemsByList = items.groupBy { it.watchlistId }
        return lists.sortedWith(compareBy({ it.position }, { it.id })).map { list ->
            Watchlist(
                id = list.id,
                name = list.name,
                builtIn = list.builtIn,
                position = list.position,
                entries = itemsByList[list.id].orEmpty()
                    .sortedWith(compareBy({ it.position }, { it.addedAt }))
                    .map { WatchlistEntry(it, byRef[ChannelRef(it.playlistId, it.stableKey)], names[it.playlistId].orEmpty()) },
            )
        }
    }
}

/** The user's watchlists (PLAN.md §18): channels from any playlist; Favorites is built in. */
class WatchlistRepository(private val db: AppDatabase) {
    private val dao = db.watchlists()

    val watchlists: Flow<List<Watchlist>> = combine(
        dao.observeWatchlists(),
        dao.observeItems(),
        dao.observeWatchlistChannels(),
        db.playlists().observeAll(),
    ) { lists, items, channels, playlists -> Watchlists.build(lists, items, channels, playlists) }
        .flowOn(Dispatchers.Default)

    suspend fun current(): List<Watchlist> =
        Watchlists.build(dao.watchlists(), dao.items(), dao.allChannels(), db.playlists().getAll())

    /** Returns the new list's id, or null when the name is empty or already used. */
    suspend fun create(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || dao.watchlists().any { it.name.equals(trimmed, ignoreCase = true) }) return null
        return dao.insert(WatchlistEntity(name = trimmed, position = dao.lastListPosition() + 1))
    }

    suspend fun rename(list: Watchlist, name: String): Boolean {
        val trimmed = name.trim()
        val others = dao.watchlists().filter { it.id != list.id }
        if (trimmed.isEmpty() || others.any { it.name.equals(trimmed, ignoreCase = true) }) return false
        val entity = dao.watchlists().firstOrNull { it.id == list.id } ?: return false
        dao.update(entity.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
        return true
    }

    /** Favorites can't be deleted. */
    suspend fun delete(list: Watchlist) = dao.delete(list.id)

    suspend fun add(listId: Long, channel: Channel) {
        db.withTransaction {
            if (dao.items(listId).any { it.playlistId == channel.playlistId && it.stableKey == channel.stableKey }) return@withTransaction
            dao.upsertItem(WatchlistItem(listId, channel.playlistId, channel.stableKey, dao.lastPosition(listId) + 1))
        }
    }

    suspend fun remove(listId: Long, ref: ChannelRef) = dao.removeItem(listId, ref.playlistId, ref.stableKey)

    suspend fun toggle(listId: Long, channel: Channel) {
        val inList = dao.items(listId).any { it.playlistId == channel.playlistId && it.stableKey == channel.stableKey }
        if (inList) remove(listId, channel.ref) else add(listId, channel)
    }

    /** [ordered] is the list after the user moved [moved]; writes one row unless positions ran out. */
    suspend fun reorderEntries(listId: Long, ordered: List<WatchlistEntry>, moved: WatchlistEntry) {
        val index = ordered.indexOfFirst { it.ref == moved.ref }
        if (index < 0) return
        val position = Positions.between(ordered.getOrNull(index - 1)?.item?.position, ordered.getOrNull(index + 1)?.item?.position)
        if (position != null) {
            dao.upsertItem(moved.item.copy(watchlistId = listId, position = position))
        } else {
            dao.upsertItems(ordered.mapIndexed { i, entry -> entry.item.copy(position = (i + 1).toDouble()) })
        }
    }

    suspend fun reorderLists(ordered: List<Watchlist>, moved: Watchlist) {
        val index = ordered.indexOfFirst { it.id == moved.id }
        if (index < 0) return
        val entities = dao.watchlists().associateBy { it.id }
        val position = Positions.between(ordered.getOrNull(index - 1)?.position, ordered.getOrNull(index + 1)?.position)
        db.withTransaction {
            if (position != null) {
                entities[moved.id]?.let { dao.update(it.copy(position = position)) }
            } else {
                ordered.forEachIndexed { i, list -> entities[list.id]?.let { dao.update(it.copy(position = (i + 1).toDouble())) } }
            }
        }
    }
}
