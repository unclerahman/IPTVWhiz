package com.iptvwhiz.app

import android.content.Context
import com.iptvwhiz.app.data.AppPrefs
import com.iptvwhiz.app.data.OverlayRepository
import com.iptvwhiz.app.data.HealthRepository
import com.iptvwhiz.app.data.PlaylistRepository
import com.iptvwhiz.app.data.WatchlistRepository
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.backup.BackupRepository
import com.iptvwhiz.app.data.epg.EpgRepository
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies. Small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** For work that outlives any screen (e.g. checking guide freshness at startup). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("User-Agent", USER_AGENT).build())
            }
        }
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val prefs = AppPrefs(context)
    val db = AppDatabase.create(context)
    val xtream = XtreamClient(http, json)
    val playlists = PlaylistRepository(context, db, http, xtream, prefs)
    val overlay = OverlayRepository(db)
    val backups = BackupRepository(db, playlists)
    val epg = EpgRepository(db, http)
    val watchlists = WatchlistRepository(db)
    val health = HealthRepository(db, http)

    /** Channels the player zaps through when they don't come from a group or watchlist (search results). */
    var playQueue: List<Channel> = emptyList()

    companion object {
        /**
         * Many IPTV servers and CDNs only answer known players and return 403 to anything else
         * (seen on Free-TV channels). VLC's is the most widely accepted. A per-playlist override
         * is planned for providers that need something else.
         */
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
