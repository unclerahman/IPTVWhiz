package com.iptvwhiz.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.ChannelEntity
import com.iptvwhiz.app.data.db.GroupInfo
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.data.m3u.M3uParser
import com.iptvwhiz.app.data.m3u.M3uPlaylist
import com.iptvwhiz.app.data.xtream.XtreamClient
import com.iptvwhiz.app.data.xtream.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where a playlist comes from. */
sealed interface PlaylistSource {
    data class M3uUrl(val url: String, val epgUrl: String? = null) : PlaylistSource
    data class M3uFile(val uri: Uri, val epgUrl: String? = null) : PlaylistSource
    data class Xtream(val credentials: XtreamCredentials) : PlaylistSource
}

/** Group name used when the provider gives a channel none. */
const val UNCATEGORIZED = "Uncategorized"

class PlaylistRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val xtream: XtreamClient,
    private val prefs: AppPrefs,
) {
    val playlists: Flow<List<PlaylistEntity>> = db.playlists().observeAll()

    fun groups(playlistId: Long): Flow<List<GroupInfo>> = db.channels().observeGroups(playlistId)

    /** [group] null = all channels. */
    fun channels(playlistId: Long, group: String?): Flow<List<ChannelEntity>> =
        if (group == null) db.channels().observeAll(playlistId) else db.channels().observeGroup(playlistId, group)

    /**
     * Downloads and parses first, and saves only if that worked, so a bad link never leaves
     * a half-added playlist behind. Returns the new playlist id. Throws [ImportException].
     */
    suspend fun add(name: String, source: PlaylistSource, onProgress: (Int) -> Unit): Long {
        val fetched = fetch(source, onProgress)
        val now = System.currentTimeMillis()
        val playlist = when (source) {
            is PlaylistSource.M3uUrl -> PlaylistEntity(
                name = name, type = PlaylistType.M3U_URL, source = source.url,
                epgUrl = source.epgUrl ?: fetched.epgUrl,
            )
            is PlaylistSource.M3uFile -> PlaylistEntity(
                name = name, type = PlaylistType.M3U_FILE, source = source.uri.toString(),
                epgUrl = source.epgUrl ?: fetched.epgUrl,
            )
            is PlaylistSource.Xtream -> PlaylistEntity(
                name = name, type = PlaylistType.XTREAM, source = source.credentials.server,
                username = source.credentials.username, password = source.credentials.password,
                epgUrl = xtream.epgUrl(source.credentials),
            )
        }.copy(channelCount = fetched.channels.size, lastRefreshed = now)

        val id = db.withTransaction {
            val id = db.playlists().insert(playlist)
            fetched.channels.chunked(BATCH).forEach { batch ->
                db.channels().insertAll(batch.map { it.copy(playlistId = id) })
            }
            id
        }
        if (prefs.activePlaylistId.value == AppPrefs.NONE) prefs.setActivePlaylist(id)
        return id
    }

    /** Re-downloads a playlist and swaps its channels in one transaction. Throws [ImportException]. */
    suspend fun refresh(playlistId: Long, onProgress: (Int) -> Unit = {}) {
        val playlist = db.playlists().get(playlistId) ?: return
        val fetched = fetch(playlist.toSource(), onProgress)
        db.withTransaction {
            db.channels().deleteForPlaylist(playlistId)
            fetched.channels.chunked(BATCH).forEach { batch ->
                db.channels().insertAll(batch.map { it.copy(playlistId = playlistId) })
            }
            db.playlists().update(
                playlist.copy(
                    channelCount = fetched.channels.size,
                    lastRefreshed = System.currentTimeMillis(),
                    epgUrl = playlist.epgUrl ?: fetched.epgUrl,
                ),
            )
        }
    }

    suspend fun delete(playlistId: Long) {
        db.playlists().delete(playlistId)
        if (prefs.activePlaylistId.value == playlistId) prefs.setActivePlaylist(AppPrefs.NONE)
    }

    /** A readable default name for a new playlist. */
    fun suggestName(source: PlaylistSource): String = when (source) {
        is PlaylistSource.M3uUrl -> source.url.toHostName() ?: "My playlist"
        is PlaylistSource.M3uFile -> displayName(source.uri)?.substringBeforeLast('.') ?: "My playlist"
        is PlaylistSource.Xtream -> source.credentials.server.toHostName() ?: "My provider"
    }

    private class Fetched(val channels: List<ChannelEntity>, val epgUrl: String?)

    private suspend fun fetch(source: PlaylistSource, onProgress: (Int) -> Unit): Fetched = try {
        when (source) {
            is PlaylistSource.M3uUrl -> fromM3u(downloadM3u(source.url, onProgress))
            is PlaylistSource.M3uFile -> fromM3u(readM3uFile(source.uri, onProgress))
            is PlaylistSource.Xtream -> fetchXtream(source.credentials, onProgress)
        }.also { if (it.channels.isEmpty()) throw ImportException(ImportError.Empty) }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        throw e as? ImportException ?: ImportException(e.toImportError(), e)
    }

    private suspend fun downloadM3u(url: String, onProgress: (Int) -> Unit): M3uPlaylist =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url.trim()).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ImportException(httpError(response.code))
                val body = response.body ?: throw ImportException(ImportError.NotAPlaylist)
                body.charStream().buffered().use { M3uParser.parse(it, onProgress) }
                    .also { if (it.entries.isEmpty()) throw ImportException(ImportError.NotAPlaylist) }
            }
        }

    private suspend fun readM3uFile(uri: Uri, onProgress: (Int) -> Unit): M3uPlaylist =
        withContext(Dispatchers.IO) {
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: throw ImportException(ImportError.FileUnreadable)
            stream.bufferedReader().use { M3uParser.parse(it, onProgress) }
                .also { if (it.entries.isEmpty()) throw ImportException(ImportError.NotAPlaylist) }
        }

    private fun fromM3u(playlist: M3uPlaylist): Fetched {
        val keys = StableKeys()
        val channels = playlist.entries.mapIndexed { index, entry ->
            val group = entry.group ?: UNCATEGORIZED
            ChannelEntity(
                playlistId = 0,
                stableKey = keys.forM3u(entry.tvgId, entry.name, group),
                number = index + 1,
                name = entry.name,
                streamUrl = entry.url,
                logoUrl = entry.logo,
                tvgId = entry.tvgId,
                groupTitle = group,
                userAgent = entry.userAgent,
                referrer = entry.referrer,
            )
        }
        return Fetched(channels, playlist.epgUrls.firstOrNull())
    }

    private suspend fun fetchXtream(credentials: XtreamCredentials, onProgress: (Int) -> Unit): Fetched {
        xtream.authenticate(credentials)
        val keys = StableKeys()
        val channels = xtream.liveChannels(credentials).mapIndexed { index, channel ->
            ChannelEntity(
                playlistId = 0,
                stableKey = keys.forXtream(channel.streamId),
                number = index + 1,
                name = channel.name,
                streamUrl = xtream.streamUrl(credentials, channel.streamId),
                logoUrl = channel.logo,
                tvgId = channel.epgChannelId,
                groupTitle = channel.category ?: UNCATEGORIZED,
            )
        }
        onProgress(channels.size)
        return Fetched(channels, xtream.epgUrl(credentials))
    }

    private fun PlaylistEntity.toSource(): PlaylistSource = when (type) {
        PlaylistType.M3U_URL -> PlaylistSource.M3uUrl(source, epgUrl)
        PlaylistType.M3U_FILE -> PlaylistSource.M3uFile(Uri.parse(source), epgUrl)
        PlaylistType.XTREAM -> PlaylistSource.Xtream(
            XtreamCredentials(source, username.orEmpty(), password.orEmpty()),
        )
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /**
     * The host is a good name for a provider's server, but not for playlists hosted on GitHub,
     * where every playlist would be called "raw.githubusercontent.com".
     */
    private fun String.toHostName(): String? {
        val withScheme = if (contains("://")) this else "http://$this"
        val uri = runCatching { Uri.parse(withScheme.trim()) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: return null
        val segments = uri.pathSegments
        return when {
            (host == "raw.githubusercontent.com" || host == "github.com") && segments.size >= 2 ->
                "${segments[0]}/${segments[1]}"
            host.endsWith(".github.io") && segments.isNotEmpty() ->
                "${host.removeSuffix(".github.io")}/${segments[0]}"
            else -> host
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
