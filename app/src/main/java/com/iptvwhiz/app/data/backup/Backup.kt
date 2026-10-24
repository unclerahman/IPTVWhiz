package com.iptvwhiz.app.data.backup

import com.iptvwhiz.app.data.db.ChannelOverride
import com.iptvwhiz.app.data.db.GroupSetting
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.data.xtream.XtreamClient
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * The backup file (PLAN.md §15, option A). Playlists are matched across devices by [BackupPlaylist.fingerprint],
 * so edits land on the same provider even though database ids differ.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupFile(
    // Always written, even though they're defaults: they're how a restore recognises the file.
    @EncodeDefault val format: String = FORMAT,
    @EncodeDefault val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val playlists: List<BackupPlaylist>,
) {
    companion object {
        const val FORMAT = "iptvwhiz-backup"
    }
}

@Serializable
data class BackupPlaylist(
    val fingerprint: String,
    val name: String,
    val type: PlaylistType,
    /** Link or server. Only present when the user chose to include logins (M3U links usually contain them). */
    val source: String? = null,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val channels: List<BackupChannel> = emptyList(),
    val groups: List<BackupGroup> = emptyList(),
)

@Serializable
data class BackupChannel(
    val key: String,
    val customName: String? = null,
    val customLogo: String? = null,
    val customGroup: String? = null,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    val sortOrder: Double? = null,
    val updatedAt: Long,
)

@Serializable
data class BackupGroup(
    val name: String,
    val customName: String? = null,
    val hidden: Boolean = false,
    val sortOrder: Double? = null,
    val isCustom: Boolean = false,
    val updatedAt: Long,
)

/** What a restore did, for the message shown afterwards. */
data class RestoreSummary(
    val merged: Int,
    val added: Int,
    val skipped: Int,
    val failed: Int,
    val changes: Int,
)

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NotABackup, NewerVersion, Unreadable }
}

object Backups {
    /**
     * Identifies a provider without revealing the login: a hash of type, normalised address and username.
     * Local file playlists can't be matched across devices (their address is device-specific).
     */
    fun fingerprint(playlist: PlaylistEntity): String {
        val address = when (playlist.type) {
            PlaylistType.XTREAM -> runCatching { XtreamClient.baseUrl(playlist.source).toString() }.getOrDefault(playlist.source)
            else -> playlist.source.trim()
        }
        val text = "${playlist.type}|${address.lowercase()}|${playlist.username.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun toBackup(playlist: PlaylistEntity, overrides: List<ChannelOverride>, groups: List<GroupSetting>, includeLogins: Boolean) =
        BackupPlaylist(
            fingerprint = fingerprint(playlist),
            name = playlist.name,
            type = playlist.type,
            source = playlist.source.takeIf { includeLogins && playlist.type != PlaylistType.M3U_FILE },
            username = playlist.username.takeIf { includeLogins },
            password = playlist.password.takeIf { includeLogins },
            // Xtream guide links contain the login; M3U guide links are usually public.
            epgUrl = playlist.epgUrl.takeIf { includeLogins || playlist.type != PlaylistType.XTREAM },
            userAgent = playlist.userAgent,
            channels = overrides.map {
                BackupChannel(it.stableKey, it.customName, it.customLogo, it.customGroup, it.hidden, it.favorite, it.sortOrder, it.updatedAt)
            },
            groups = groups.map { BackupGroup(it.name, it.customName, it.hidden, it.sortOrder, it.isCustom, it.updatedAt) },
        )

    /** Channel edits from [incoming] that are newer than this device's (or new here). Newest change wins. */
    fun channelsToApply(playlistId: Long, local: List<ChannelOverride>, incoming: List<BackupChannel>): List<ChannelOverride> {
        val byKey = local.associateBy { it.stableKey }
        return incoming
            .filter { remote -> byKey[remote.key].let { it == null || remote.updatedAt > it.updatedAt } }
            .map {
                ChannelOverride(playlistId, it.key, it.customName, it.customLogo, it.customGroup, it.hidden, it.favorite, it.sortOrder, it.updatedAt)
            }
    }

    fun groupsToApply(playlistId: Long, local: List<GroupSetting>, incoming: List<BackupGroup>): List<GroupSetting> {
        val byName = local.associateBy { it.name }
        return incoming
            .filter { remote -> byName[remote.name].let { it == null || remote.updatedAt > it.updatedAt } }
            .map { GroupSetting(playlistId, it.name, it.customName, it.hidden, it.sortOrder, it.isCustom, it.updatedAt) }
    }
}
