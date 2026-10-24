package com.iptvwhiz.app.data

/**
 * Builds the key that identifies a channel across playlist refreshes (PLAN.md §4).
 * The stream URL is never used: providers rotate tokens in URLs.
 */
class StableKeys {
    private val seen = HashMap<String, Int>()

    fun forXtream(streamId: String): String = unique("x:$streamId")

    fun forM3u(tvgId: String?, name: String, group: String): String {
        val base = if (!tvgId.isNullOrBlank()) {
            "t:${normalize(tvgId)}|${normalize(name)}"
        } else {
            "n:${normalize(group)}|${normalize(name)}"
        }
        return unique(base)
    }

    /** Identical channels in one playlist get #2, #3… in order, which stays stable across refreshes. */
    private fun unique(base: String): String {
        val count = (seen[base] ?: 0) + 1
        seen[base] = count
        return if (count == 1) base else "$base#$count"
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(whitespace, " ")

    private companion object {
        val whitespace = Regex("\\s+")
    }
}
