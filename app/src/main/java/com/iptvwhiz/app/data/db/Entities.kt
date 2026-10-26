package com.iptvwhiz.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { M3U_URL, M3U_FILE, XTREAM }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** M3U link, content:// file URI, or Xtream server address. */
    val source: String,
    // TODO(before Play): move credentials to Keystore-backed storage (PLAN.md §10).
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val channelCount: Int = 0,
    val lastRefreshed: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A channel exactly as the provider sent it. User edits will live in a separate overlay table
 * keyed by [stableKey] (PLAN.md §4), so a refresh can replace these rows freely.
 */
@Entity(
    tableName = "channels",
    primaryKeys = ["playlistId", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "groupTitle"), Index("playlistId", "number")],
)
data class ChannelEntity(
    val playlistId: Long,
    val stableKey: String,
    /** 1-based position in the provider's order; shown as the channel number. */
    val number: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val groupTitle: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** tvg-country from the playlist (two-letter code), when it has one. */
    val country: String? = null,
)
