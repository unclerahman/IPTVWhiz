package com.iptvwhiz.app.data.db

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * The user's edits to one channel (PLAN.md §4). Keyed by the channel's stable key and linked
 * only to the playlist, never to the channel row, so a refresh that replaces channels keeps them.
 * Every field null/false means "as the provider sent it".
 */
@Entity(
    tableName = "channel_overrides",
    primaryKeys = ["playlistId", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChannelOverride(
    val playlistId: Long,
    val stableKey: String,
    val customName: String? = null,
    val customLogo: String? = null,
    /** Group key the user moved the channel to. */
    val customGroup: String? = null,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    /** Position inside its group. Fractional, so a move rewrites one row (PLAN.md §15). */
    val sortOrder: Double? = null,
    /** For merging backups (and sync later): newest change wins. */
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Per-group edits, and the user's own (custom) groups. [name] is the group's key. */
@Entity(
    tableName = "group_settings",
    primaryKeys = ["playlistId", "name"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GroupSetting(
    val playlistId: Long,
    val name: String,
    val customName: String? = null,
    val hidden: Boolean = false,
    val sortOrder: Double? = null,
    val isCustom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A channel as the user sees it: provider data with their edits applied. */
@DatabaseView(
    viewName = "channel_view",
    value = """
        SELECT c.playlistId, c.stableKey, c.number,
            COALESCE(o.customName, c.name) AS name, c.name AS originalName,
            COALESCE(o.customLogo, c.logoUrl) AS logoUrl,
            c.streamUrl, c.tvgId,
            COALESCE(o.customGroup, c.groupTitle) AS groupTitle, c.groupTitle AS originalGroup,
            c.userAgent, c.referrer, c.country,
            c.drmType, c.drmKey, c.drmLicenseUrl, c.drmHeaders,
            COALESCE(o.hidden, 0) AS hidden, COALESCE(o.favorite, 0) AS favorite,
            COALESCE(o.sortOrder, c.number) AS sortOrder
        FROM channels c
        LEFT JOIN channel_overrides o ON o.playlistId = c.playlistId AND o.stableKey = c.stableKey
    """,
)
data class Channel(
    val playlistId: Long,
    val stableKey: String,
    val number: Int,
    val name: String,
    val originalName: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val groupTitle: String,
    val originalGroup: String,
    val userAgent: String?,
    val referrer: String?,
    /** tvg-country as given by the playlist; see [effectiveCountry]. */
    val country: String?,
    val hidden: Boolean,
    val favorite: Boolean,
    val sortOrder: Double,
    /** DRM from the provider playlist; see [com.iptvwhiz.app.data.drm.drmInfo]. */
    val drmType: String? = null,
    val drmKey: String? = null,
    val drmLicenseUrl: String? = null,
    val drmHeaders: String? = null,
)

/**
 * Two-letter country code: the playlist's tvg-country, else the tvg-id suffix
 * ("Kanali7.al" → "AL", "CNN.us@SD" → "US"). Null when neither says.
 */
val Channel.effectiveCountry: String?
    get() {
        country?.split(',', ';')?.firstOrNull()?.trim()?.takeIf { it.length == 2 }?.let { return it.uppercase() }
        val suffix = tvgId?.substringBefore('@')?.substringAfterLast('.', "")?.trim()
        return suffix?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
    }
