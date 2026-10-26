package com.iptvwhiz.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    suspend fun getAll(): List<PlaylistEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)

}

@Dao
interface OverlayDao {
    @Query("SELECT * FROM channel_view WHERE playlistId = :playlistId")
    fun observeChannels(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channel_view WHERE playlistId = :playlistId")
    suspend fun channels(playlistId: Long): List<Channel>

    @Query("SELECT * FROM channel_overrides WHERE playlistId = :playlistId AND stableKey = :stableKey")
    suspend fun override(playlistId: Long, stableKey: String): ChannelOverride?

    @Query("SELECT * FROM channel_overrides WHERE playlistId = :playlistId")
    suspend fun allOverrides(playlistId: Long): List<ChannelOverride>

    @Query("SELECT * FROM channel_overrides WHERE playlistId = :playlistId AND stableKey IN (:stableKeys)")
    suspend fun overrides(playlistId: Long, stableKeys: List<String>): List<ChannelOverride>

    @Upsert
    suspend fun upsert(override: ChannelOverride)

    @Upsert
    suspend fun upsertAll(overrides: List<ChannelOverride>)

    /** Channels in a deleted custom group go back to their provider group. */
    @Query(
        """
        UPDATE channel_overrides SET customGroup = NULL, sortOrder = NULL, updatedAt = :now
        WHERE playlistId = :playlistId AND customGroup = :group
        """,
    )
    suspend fun releaseGroup(playlistId: Long, group: String, now: Long)

    @Query("SELECT * FROM group_settings WHERE playlistId = :playlistId")
    fun observeGroupSettings(playlistId: Long): Flow<List<GroupSetting>>

    @Query("SELECT * FROM group_settings WHERE hidden = 1")
    fun observeHiddenGroups(): Flow<List<GroupSetting>>

    @Query("SELECT * FROM group_settings WHERE playlistId = :playlistId")
    suspend fun groupSettings(playlistId: Long): List<GroupSetting>

    @Upsert
    suspend fun upsertGroup(setting: GroupSetting)

    @Upsert
    suspend fun upsertGroups(settings: List<GroupSetting>)

    @Query("DELETE FROM group_settings WHERE playlistId = :playlistId AND name = :name")
    suspend fun deleteGroup(playlistId: Long, name: String)
}
