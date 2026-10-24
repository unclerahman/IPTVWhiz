package com.iptvwhiz.app.data.backup

import androidx.room.withTransaction
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.PlaylistRepository
import com.iptvwhiz.app.data.PlaylistSource
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.data.xtream.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Saves playlists and edits to a JSON file and merges such a file back in. */
class BackupRepository(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Writes every playlist and its edits. Returns the number of playlists saved. */
    suspend fun export(output: OutputStream, includeLogins: Boolean): Int = withContext(Dispatchers.IO) {
        val entries = db.playlists().getAll().map { playlist ->
            Backups.toBackup(
                playlist,
                db.overlay().allOverrides(playlist.id),
                db.overlay().groupSettings(playlist.id),
                includeLogins,
            )
        }
        val file = BackupFile(exportedAt = System.currentTimeMillis(), appVersion = BuildConfig.VERSION_NAME, playlists = entries)
        output.bufferedWriter().use { it.write(json.encodeToString(BackupFile.serializer(), file)) }
        entries.size
    }

    /**
     * Merges a backup into this device. Playlists already here get the newer of each edit;
     * playlists not here are added when the file has their link (logins included), otherwise skipped.
     * [onProgress] reports the playlist being added, which can take a while to download.
     */
    suspend fun restore(input: InputStream, onProgress: (String) -> Unit = {}): RestoreSummary {
        val file = withContext(Dispatchers.IO) { read(input) }
        val local = db.playlists().getAll().associateBy { Backups.fingerprint(it) }
        var merged = 0
        var added = 0
        var skipped = 0
        var failed = 0
        var changes = 0

        for (entry in file.playlists) {
            val existing = local[entry.fingerprint]
            val playlistId = when {
                existing != null -> existing.id.also { merged++ }
                else -> {
                    val source = entry.toSource()
                    if (source == null) {
                        skipped++
                        continue
                    }
                    onProgress(entry.name)
                    try {
                        playlists.add(entry.name, source) {}.also { added++ }
                    } catch (e: ImportException) {
                        failed++
                        continue
                    }
                }
            }
            changes += apply(playlistId, entry)
        }
        return RestoreSummary(merged, added, skipped, failed, changes)
    }

    private suspend fun apply(playlistId: Long, entry: BackupPlaylist): Int = db.withTransaction {
        val channels = Backups.channelsToApply(playlistId, db.overlay().allOverrides(playlistId), entry.channels)
        val groups = Backups.groupsToApply(playlistId, db.overlay().groupSettings(playlistId), entry.groups)
        if (channels.isNotEmpty()) db.overlay().upsertAll(channels)
        if (groups.isNotEmpty()) db.overlay().upsertGroups(groups)
        channels.size + groups.size
    }

    private fun read(input: InputStream): BackupFile {
        val text = try {
            input.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.Unreadable, e)
        }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.NotABackup, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NotABackup, e)
        }
        if (file.format != BackupFile.FORMAT) throw BackupException(BackupException.Reason.NotABackup)
        if (file.version > 1) throw BackupException(BackupException.Reason.NewerVersion)
        return file
    }

    private fun BackupPlaylist.toSource(): PlaylistSource? {
        val address = source ?: return null
        return when (type) {
            PlaylistType.M3U_URL -> PlaylistSource.M3uUrl(address, epgUrl)
            PlaylistType.XTREAM -> PlaylistSource.Xtream(
                XtreamCredentials(address, username ?: return null, password ?: return null),
            )
            PlaylistType.M3U_FILE -> null
        }
    }
}
