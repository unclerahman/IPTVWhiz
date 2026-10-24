package com.iptvwhiz.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

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

    @Query(
        """
        SELECT groupTitle AS name, COUNT(*) AS channelCount, MIN(number) AS firstNumber
        FROM channels WHERE playlistId = :playlistId
        GROUP BY groupTitle ORDER BY firstNumber
        """,
    )
    fun observeGroups(playlistId: Long): Flow<List<GroupInfo>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number")
    fun observeAll(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group ORDER BY number")
    fun observeGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>
}
