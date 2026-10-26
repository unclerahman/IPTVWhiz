package com.iptvwhiz.app.data

import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.ChannelHealth
import com.iptvwhiz.app.data.db.HealthStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HealthSummary(val working: Int, val offline: Int, val blocked: Int)

/**
 * Tells working channels from dead ones (PLAN.md §18): an on-demand check of a list of channels,
 * plus whatever the player learns while playing. Results older than a week are ignored.
 */
class HealthRepository(private val db: AppDatabase, http: OkHttpClient) {
    private val dao = db.health()

    private val probeHttp = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun statuses(): Flow<Map<ChannelRef, HealthStatus>> =
        dao.observeSince(System.currentTimeMillis() - MAX_AGE).map { rows ->
            rows.associate { ChannelRef(it.playlistId, it.stableKey) to it.status }
        }

    suspend fun record(channel: Channel, status: HealthStatus) =
        dao.record(ChannelHealth(channel.playlistId, channel.stableKey, status, System.currentTimeMillis()))

    /** Probes [channels] a few at a time. [onProgress] gets (checked, total). Cancel to stop early. */
    suspend fun check(channels: List<Channel>, onProgress: (Int, Int) -> Unit): HealthSummary = coroutineScope {
        val gate = Semaphore(PARALLEL)
        val lock = Mutex()
        var done = 0
        val pending = ArrayList<ChannelHealth>()
        onProgress(0, channels.size)
        val results = channels.map { channel ->
            async(Dispatchers.IO) {
                val status = gate.withPermit { probe(channel) }
                lock.withLock {
                    done++
                    pending += ChannelHealth(channel.playlistId, channel.stableKey, status, System.currentTimeMillis())
                    if (pending.size >= 50) {
                        dao.recordAll(pending.toList())
                        pending.clear()
                    }
                    onProgress(done, channels.size)
                }
                status
            }
        }.awaitAll()
        lock.withLock { if (pending.isNotEmpty()) dao.recordAll(pending.toList()) }
        HealthSummary(
            working = results.count { it == HealthStatus.Working },
            offline = results.count { it == HealthStatus.Offline },
            blocked = results.count { it == HealthStatus.Blocked },
        )
    }

    private suspend fun probe(channel: Channel): HealthStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(channel.streamUrl)
            .header("User-Agent", channel.userAgent ?: AppContainer.USER_AGENT)
            .apply { channel.referrer?.let { header("Referer", it) } }
            .build()
        try {
            probeHttp.newCall(request).execute().use { response ->
                val head = if (response.isSuccessful) response.body?.source()?.let { source ->
                    source.request(PEEK_BYTES)
                    source.buffer.snapshot().utf8().take(PEEK_BYTES.toInt())
                } else {
                    null
                }
                HealthProbe.classify(response.code, response.header("Content-Type"), head)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            HealthStatus.Offline
        } catch (e: IllegalArgumentException) {
            HealthStatus.Offline // not a valid URL
        }
    }

    private companion object {
        const val PARALLEL = 12
        const val PEEK_BYTES = 512L
        const val MAX_AGE = 7 * 24 * 3_600_000L
    }
}

/** Turns an HTTP answer into a health status. Separate so it can be tested without a network. */
object HealthProbe {
    fun classify(code: Int, contentType: String?, head: String?): HealthStatus = when {
        code == 401 || code == 403 || code == 451 -> HealthStatus.Blocked
        code !in 200..299 -> HealthStatus.Offline
        head.isNullOrEmpty() -> HealthStatus.Offline
        // A web page instead of a stream (e.g. YouTube or Twitch links): the player can't use it.
        contentType?.contains("text/html", ignoreCase = true) == true -> HealthStatus.Offline
        head.trimStart().startsWith("<!doctype", ignoreCase = true) || head.trimStart().startsWith("<html", ignoreCase = true) ->
            HealthStatus.Offline
        else -> HealthStatus.Working
    }
}
