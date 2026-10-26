package com.iptvwhiz.app.data

import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.effectiveCountry
import java.text.Normalizer

data class SearchFilters(
    val query: String = "",
    /** Two-letter code, or null for any country. */
    val country: String? = null,
    /** Group name as shown in the channel's playlist, or null for any group. */
    val group: String? = null,
    val playlistId: Long? = null,
) {
    val isEmpty get() = query.isBlank() && country == null && group == null && playlistId == null
}

/** Search across all playlists by name, country, group and playlist (PLAN.md §18). Pure, so it's testable. */
object ChannelSearch {
    const val MAX_RESULTS = 500

    /**
     * [channels] are every playlist's channels; hidden channels and channels in [hiddenGroups]
     * (playlistId to group name) are left out, as in browsing.
     */
    fun search(channels: List<Channel>, hiddenGroups: Set<Pair<Long, String>>, filters: SearchFilters): List<Channel> {
        if (filters.isEmpty) return emptyList()
        val words = normalize(filters.query).split(' ').filter { it.isNotEmpty() }
        return channels.asSequence()
            .filter { !it.hidden && (it.playlistId to it.groupTitle) !in hiddenGroups }
            .filter { filters.playlistId == null || it.playlistId == filters.playlistId }
            .filter { filters.country == null || it.effectiveCountry == filters.country }
            .filter { filters.group == null || it.groupTitle == filters.group }
            .map { it to normalize(it.name) }
            .filter { (_, name) -> words.all { name.contains(it) } }
            // Names that start with the search come first, then the playlist's own order.
            .sortedWith(
                compareBy<Pair<Channel, String>>(
                    { (_, name) -> if (words.isNotEmpty() && name.startsWith(words.first())) 0 else 1 },
                    { (channel, _) -> channel.playlistId },
                    { (channel, _) -> channel.number },
                ),
            )
            .map { it.first }
            .take(MAX_RESULTS)
            .toList()
    }

    /** Countries present among [channels], most channels first: code to count. */
    fun countries(channels: List<Channel>): List<Pair<String, Int>> =
        channels.mapNotNull { it.effectiveCountry }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

    /** Groups present among [channels], biggest first. */
    fun groups(channels: List<Channel>): List<Pair<String, Int>> =
        channels.groupingBy { it.groupTitle }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

    /** Lowercase, accents removed, punctuation turned into spaces: "Télé-Québec" → "tele quebec". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
