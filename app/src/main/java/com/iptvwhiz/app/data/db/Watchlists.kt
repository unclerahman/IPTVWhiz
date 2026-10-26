package com.iptvwhiz.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A list of channels the user builds from any playlists (PLAN.md §18). Favorites is the built-in one. */
@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val builtIn: Boolean = false,
    val position: Double,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val FAVORITES_ID = 1L
    }
}

/**
 * One channel in a watchlist, by playlist + stable key, so it survives playlist refreshes.
 * Deleting the playlist or the watchlist removes it; a channel missing after a refresh does not.
 */
@Entity(
    tableName = "watchlist_items",
    primaryKeys = ["watchlistId", "playlistId", "stableKey"],
    foreignKeys = [
        ForeignKey(entity = WatchlistEntity::class, parentColumns = ["id"], childColumns = ["watchlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId")],
)
data class WatchlistItem(
    val watchlistId: Long,
    val playlistId: Long,
    val stableKey: String,
    val position: Double,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class HealthStatus { Working, Offline, Blocked }

/** Last known result of trying a channel's stream, from a check or from playing it. */
@Entity(
    tableName = "channel_health",
    primaryKeys = ["playlistId", "stableKey"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChannelHealth(
    val playlistId: Long,
    val stableKey: String,
    val status: HealthStatus,
    val checkedAt: Long,
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlists ORDER BY position, id")
    fun observeWatchlists(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlists ORDER BY position, id")
    suspend fun watchlists(): List<WatchlistEntity>

    @Insert
    suspend fun insert(watchlist: WatchlistEntity): Long

    @Update
    suspend fun update(watchlist: WatchlistEntity)

    @Query("DELETE FROM watchlists WHERE id = :id AND builtIn = 0")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM watchlist_items ORDER BY watchlistId, position")
    fun observeItems(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist_items ORDER BY watchlistId, position")
    suspend fun items(): List<WatchlistItem>

    @Query("SELECT * FROM watchlist_items WHERE watchlistId = :watchlistId ORDER BY position")
    suspend fun items(watchlistId: Long): List<WatchlistItem>

    @Upsert
    suspend fun upsertItem(item: WatchlistItem)

    @Upsert
    suspend fun upsertItems(items: List<WatchlistItem>)

    @Query("DELETE FROM watchlist_items WHERE watchlistId = :watchlistId AND playlistId = :playlistId AND stableKey = :stableKey")
    suspend fun removeItem(watchlistId: Long, playlistId: Long, stableKey: String)

    /** The channels that are in any watchlist, with edits applied. */
    @Query(
        """
        SELECT DISTINCT cv.* FROM channel_view cv
        JOIN watchlist_items wi ON wi.playlistId = cv.playlistId AND wi.stableKey = cv.stableKey
        """,
    )
    fun observeWatchlistChannels(): Flow<List<Channel>>

    @Query("SELECT COALESCE(MAX(position), 0) FROM watchlist_items WHERE watchlistId = :watchlistId")
    suspend fun lastPosition(watchlistId: Long): Double

    @Query("SELECT COALESCE(MAX(position), 0) FROM watchlists")
    suspend fun lastListPosition(): Double

    /** Every playlist's channels (with edits), for search across playlists. */
    @Query("SELECT * FROM channel_view")
    fun observeAllChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channel_view")
    suspend fun allChannels(): List<Channel>
}

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(health: ChannelHealth)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAll(health: List<ChannelHealth>)

    @Query("SELECT * FROM channel_health WHERE checkedAt > :since")
    fun observeSince(since: Long): Flow<List<ChannelHealth>>
}
