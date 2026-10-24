package com.iptvwhiz.app.data.epg

import android.util.Xml
import androidx.room.withTransaction
import com.iptvwhiz.app.data.db.AppDatabase
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.ChannelEpg
import com.iptvwhiz.app.data.db.EpgStatus
import com.iptvwhiz.app.data.db.ProgramEntity
import com.iptvwhiz.app.data.db.ProgramSlot
import com.iptvwhiz.app.data.httpError
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.toImportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** What's on now and next on one channel. */
data class NowNext(val now: ProgramSlot?, val next: ProgramSlot?) {
    /** 0..1 through the current programme. */
    fun progress(at: Long): Float = now?.let { ((at - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) } ?: 0f
}

/** Downloads XMLTV guides, keeps what matches the playlist, and answers "what's on". */
class EpgRepository(private val db: AppDatabase, private val http: OkHttpClient) {
    private val dao = db.epg()

    /**
     * Re-imports every guide link of a playlist. Old guide data stays until the new import
     * finishes, so a failed update never leaves the guide empty. [onProgress] gets (finished links, all links).
     */
    suspend fun update(playlistId: Long, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val playlist = db.playlists().get(playlistId) ?: return@withContext
        val channels = db.overlay().channels(playlistId)
        val urls = selectLinks(links(playlist.epgUrl), channels)
        if (urls.isEmpty()) {
            dao.upsertStatus(EpgStatus(playlistId, System.currentTimeMillis(), 0, 0, ERROR_NO_LINK))
            return@withContext
        }

        val importStart = System.currentTimeMillis()
        val matcher = EpgMatcher(channels)
        val windowStart = importStart - KEEP_PAST
        val windowEnd = importStart + KEEP_AHEAD
        val batch = ArrayList<ProgramEntity>(BATCH)
        var failures = 0
        var lastError: String? = null

        urls.forEachIndexed { index, url ->
            onProgress(index, urls.size)
            try {
                open(url).use { stream ->
                    XmltvParser(Xml.newPullParser()).parse(
                        stream,
                        onChannel = matcher::addGuideChannel,
                        onProgramme = { programme ->
                            if (programme.stop > windowStart && programme.start < windowEnd && matcher.wants(programme.channelId)) {
                                batch += ProgramEntity(
                                    playlistId, programme.channelId, programme.start, programme.stop,
                                    programme.title, programme.description, importStart,
                                )
                                if (batch.size >= BATCH) {
                                    dao.insertPrograms(batch.toList())
                                    batch.clear()
                                }
                            }
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                lastError = (e as? ImportException)?.error?.toString() ?: e.toImportError().toString()
            }
        }
        if (batch.isNotEmpty()) dao.insertPrograms(batch.toList())
        onProgress(urls.size, urls.size)

        if (failures == urls.size) {
            // Nothing new arrived: keep the old guide, report the problem.
            val previous = dao.status(playlistId)
            dao.upsertStatus(
                EpgStatus(playlistId, previous?.lastUpdated ?: 0, previous?.matchedChannels ?: 0, previous?.programmes ?: 0, lastError),
            )
            return@withContext
        }
        db.withTransaction {
            dao.deleteOlderImports(playlistId, importStart)
            dao.clearChannelMap(playlistId)
            dao.insertChannelMap(matcher.mapping.map { (key, epgId) -> ChannelEpg(playlistId, key, epgId) })
            dao.upsertStatus(
                EpgStatus(
                    playlistId = playlistId,
                    lastUpdated = System.currentTimeMillis(),
                    matchedChannels = matcher.mapping.size,
                    programmes = dao.programCount(playlistId),
                    lastError = if (failures > 0) PARTIAL_PREFIX + lastError else null,
                ),
            )
        }
    }

    fun status(playlistId: Long): Flow<EpgStatus?> = dao.observeStatus(playlistId)

    /** Now/next per channel (by stable key), refreshed every minute and after each guide update. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun nowNext(playlistId: Long): Flow<Map<String, NowNext>> =
        dao.observeStatus(playlistId).flatMapLatest { status ->
            if (status == null || status.programmes == 0) {
                flow { emit(emptyMap()) }
            } else {
                minuteTicks().map { now -> nowNext(dao.programs(playlistId, now, now + LOOK_AHEAD), now) }
            }
        }.flowOn(Dispatchers.IO)

    /** Whole programme list for the next hours, for the guide. */
    suspend fun schedule(playlistId: Long, hours: Int): Map<String, List<ProgramSlot>> {
        val now = System.currentTimeMillis()
        return dao.programs(playlistId, now, now + hours * 3_600_000L).groupBy { it.stableKey }
    }

    private fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000 + 50)
        }
    }

    private fun open(url: String): InputStream {
        val response = http.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw ImportException(httpError(response.code))
        }
        val raw = BufferedInputStream(response.body!!.byteStream())
        // .xml.gz guides are common and aren't decoded by OkHttp (that only covers Content-Encoding).
        raw.mark(2)
        val gzip = raw.read() == 0x1f && raw.read() == 0x8b
        raw.reset()
        return if (gzip) GZIPInputStream(raw, 64 * 1024) else raw
    }

    companion object {
        const val ERROR_NO_LINK = "NoLink"

        /** Marks an update where some links failed but the guide was still updated. */
        const val PARTIAL_PREFIX = "partial:"

        /**
         * At most this many guide links per update. Some playlists list a hundred (Free-TV:
         * 101 links, ~600 MB), which a TV box shouldn't download every day.
         */
        const val MAX_LINKS = 10

        fun links(epgUrl: String?): List<String> =
            epgUrl.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /**
         * Picks up to [MAX_LINKS] links. With more, links whose file name carries a country code
         * used by the visible channels' tvg-ids (".al" → "_AL1.xml.gz") go first; then playlist order.
         */
        fun selectLinks(links: List<String>, channels: List<Channel>): List<String> {
            if (links.size <= MAX_LINKS) return links
            val countryCounts = channels.asSequence()
                .filter { !it.hidden }
                .mapNotNull { it.tvgId?.substringAfterLast('.', "")?.substringBefore('@')?.lowercase() }
                .filter { it.length == 2 && it.all(Char::isLetter) }
                .groupingBy { it }.eachCount()
            fun score(link: String): Int {
                val file = link.substringAfterLast('/').lowercase()
                return countryCounts.entries.sumOf { (code, count) ->
                    // "_al1.xml", "-al.xml": the code is never part of a longer word.
                    if (Regex("(^|[^a-z])$code([^a-z]|$)").containsMatchIn(file)) count else 0
                }
            }
            return links.withIndex()
                .sortedWith(compareByDescending<IndexedValue<String>> { score(it.value) }.thenBy { it.index })
                .take(MAX_LINKS)
                .sortedBy { it.index }
                .map { it.value }
        }

        private const val BATCH = 500
        private const val KEEP_PAST = 2 * 3_600_000L
        private const val KEEP_AHEAD = 3 * 24 * 3_600_000L
        private const val LOOK_AHEAD = 12 * 3_600_000L

        fun nowNext(slots: List<ProgramSlot>, now: Long): Map<String, NowNext> =
            slots.groupBy { it.stableKey }.mapValues { (_, list) ->
                val sorted = list.sortedBy { it.start }
                val current = sorted.firstOrNull { it.start <= now && now < it.stop }
                val next = sorted.firstOrNull { it.start >= (current?.stop ?: now) && it !== current }
                NowNext(current, next)
            }
    }
}

/**
 * Decides which guide channels feed which playlist channels: tvg-id first, then the channel name.
 * Decisions are made the first time a guide channel's programme appears; XMLTV lists
 * `<channel>` elements (with their names) before programmes.
 */
class EpgMatcher(channels: List<Channel>) {
    private val byTvgId = HashMap<String, MutableList<String>>()
    private val byName = HashMap<String, MutableList<String>>()
    private val guideNames = HashMap<String, List<String>>()
    private val decided = HashMap<String, Boolean>()

    /** stableKey → guide channel id. */
    val mapping = LinkedHashMap<String, String>()

    init {
        channels.forEach { channel ->
            channel.tvgId?.takeIf { it.isNotBlank() }?.let {
                byTvgId.getOrPut(it.lowercase()) { mutableListOf() } += channel.stableKey
            }
            listOf(channel.originalName, channel.name).map(::epgNameKey).filter { it.isNotEmpty() }.distinct().forEach {
                byName.getOrPut(it) { mutableListOf() } += channel.stableKey
            }
        }
    }

    fun addGuideChannel(id: String, displayNames: List<String>) {
        guideNames[id] = displayNames
    }

    fun wants(guideChannelId: String): Boolean = decided.getOrPut(guideChannelId) {
        val viaId = byTvgId[guideChannelId.lowercase()].orEmpty().filter { it !in mapping }
        val keys = viaId.ifEmpty {
            guideNames[guideChannelId].orEmpty()
                .flatMap { byName[epgNameKey(it)].orEmpty() }
                .distinct()
                // A tvg-id that the guide knows names its channel exactly; don't let a name match override it.
                .filter { it !in mapping && it !in claimedByTvgId() }
        }
        keys.forEach { mapping[it] = guideChannelId }
        keys.isNotEmpty()
    }

    private var claimedForSize = -1
    private var claimed = emptySet<String>()

    /** Playlist channels whose tvg-id appears in the guide. Recomputed only when new guide channels arrive. */
    private fun claimedByTvgId(): Set<String> {
        if (claimedForSize != guideNames.size) {
            val ids = guideNames.keys.mapTo(HashSet()) { it.lowercase() }
            claimed = byTvgId.filterKeys { it in ids }.values.flatten().toSet()
            claimedForSize = guideNames.size
        }
        return claimed
    }
}
