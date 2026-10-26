package com.iptvwhiz.app.data.backup

import androidx.room.withTransaction
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.PlaylistRepository
import com.iptvwhiz.app.data.PlaylistSource
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.data.db.WatchlistEntity
import com.iptvwhiz.app.data.db.WatchlistItem
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
        val fingerprints = db.playlists().getAll().associate { it.id to Backups.fingerprint(it) }
        val items = db.watchlists().items().groupBy { it.watchlistId }
        val watchlists = db.watchlists().watchlists().map { list ->
            BackupWatchlist(
                name = list.name,
                builtIn = list.builtIn,
                position = list.position,
                items = items[list.id].orEmpty().mapNotNull { item ->
                    fingerprints[item.playlistId]?.let { BackupWatchlistItem(it, item.stableKey, item.position, item.addedAt) }
                },
            )
        }
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            playlists = entries,
            watchlists = watchlists,
        )
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
        // Backup fingerprint → playlist id here, including playlists the restore adds.
        val idsByFingerprint = local.mapValues { it.value.id }.toMutableMap()
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
            idsByFingerprint[entry.fingerprint] = playlistId
            changes += apply(playlistId, entry)
            changes += addToWatchlist(WatchlistEntity.FAVORITES_ID, Backups.legacyFavorites(entry).map { playlistId to it })
        }
        file.watchlists.forEach { backup ->
            val target = when {
                backup.builtIn -> WatchlistEntity.FAVORITES_ID
                else -> db.watchlists().watchlists().firstOrNull { it.name.equals(backup.name, ignoreCase = true) }?.id
                    ?: db.watchlists().insert(WatchlistEntity(name = backup.name, position = backup.position))
            }
            val refs = backup.items.sortedBy { it.position }.mapNotNull { item -> idsByFingerprint[item.playlist]?.let { it to item.key } }
            changes += addToWatchlist(target, refs)
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

    /** Appends channels that aren't in the watchlist yet, keeping their order. Returns how many were added. */
    private suspend fun addToWatchlist(watchlistId: Long, refs: List<Pair<Long, String>>): Int {
        if (refs.isEmpty()) return 0
        val dao = db.watchlists()
        return db.withTransaction {
            val present = dao.items(watchlistId).map { it.playlistId to it.stableKey }.toSet()
            var position = dao.lastPosition(watchlistId)
            val new = refs.distinct().filter { it !in present }.map { (playlistId, key) ->
                position += 1
                WatchlistItem(watchlistId, playlistId, key, position)
            }
            if (new.isNotEmpty()) dao.upsertItems(new)
            new.size
        }
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
