package com.iptvwhiz.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One programme from the guide, kept only for channels the playlist has (PLAN.md §11). */
@Entity(
    tableName = "programs",
    primaryKeys = ["playlistId", "epgId", "start"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId", "stop")],
)
data class ProgramEntity(
    val playlistId: Long,
    val epgId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
    /** Rows from older imports are removed only after a new import succeeds. */
    val importedAt: Long,
)

/** Which guide channel feeds which playlist channel (by tvg-id, or by name when there's none). */
@Entity(
    tableName = "channel_epg",
    primaryKeys = ["playlistId", "stableKey"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChannelEpg(val playlistId: Long, val stableKey: String, val epgId: String)

@Entity(
    tableName = "epg_status",
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EpgStatus(
    @PrimaryKey val playlistId: Long,
    val lastUpdated: Long,
    val matchedChannels: Int,
    val programmes: Int,
    /** Set when the last update failed; earlier guide data is kept. */
    val lastError: String?,
)

/** A programme for one playlist channel. */
data class ProgramSlot(
    val stableKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

@Dao
interface EpgDao {
    // Blocking on purpose: called from the XML parser's callbacks on an IO thread.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPrograms(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs WHERE playlistId = :playlistId AND importedAt < :importedBefore")
    suspend fun deleteOlderImports(playlistId: Long, importedBefore: Long)

    @Query("DELETE FROM programs WHERE stop < :before")
    suspend fun deleteEnded(before: Long)

    @Query("SELECT COUNT(*) FROM programs WHERE playlistId = :playlistId")
    suspend fun programCount(playlistId: Long): Int

    @Query("DELETE FROM channel_epg WHERE playlistId = :playlistId")
    suspend fun clearChannelMap(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelMap(entries: List<ChannelEpg>)

    @Upsert
    suspend fun upsertStatus(status: EpgStatus)

    @Query("SELECT * FROM epg_status WHERE playlistId = :playlistId")
    fun observeStatus(playlistId: Long): Flow<EpgStatus?>

    @Query("SELECT * FROM epg_status WHERE playlistId = :playlistId")
    suspend fun status(playlistId: Long): EpgStatus?

    @Query(
        """
        SELECT ce.stableKey AS stableKey, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description
        FROM channel_epg ce
        JOIN programs p ON p.playlistId = ce.playlistId AND p.epgId = ce.epgId
        WHERE ce.playlistId = :playlistId AND p.stop > :from AND p.start < :until
        ORDER BY p.start
        """,
    )
    suspend fun programs(playlistId: Long, from: Long, until: Long): List<ProgramSlot>
}
