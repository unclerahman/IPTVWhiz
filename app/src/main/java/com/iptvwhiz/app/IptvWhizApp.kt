package com.iptvwhiz.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iptvwhiz.app.data.epg.EpgScheduler
import kotlinx.coroutines.launch

class IptvWhizApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        EpgScheduler.schedulePeriodic(this)
        // A guide older than a day (or never loaded) is updated now rather than at the next periodic run.
        container.appScope.launch {
            container.db.playlists().getAll().forEach { playlist ->
                val updated = container.db.epg().status(playlist.id)?.lastUpdated ?: 0
                if (System.currentTimeMillis() - updated > 24 * 3_600_000L) {
                    EpgScheduler.updateNow(this@IptvWhizApp, playlist.id)
                }
            }
        }
    }

    /** Channel logos: shared HTTP client, capped caches so cheap TV boxes stay responsive. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logos"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
}
