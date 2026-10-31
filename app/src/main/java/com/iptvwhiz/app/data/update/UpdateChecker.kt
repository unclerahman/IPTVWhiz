package com.iptvwhiz.app.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the app needs to know about the newest release on GitHub. */
data class Release(
    val version: String,
    /** Direct download link for the APK asset, when the release has one. */
    val apkUrl: String?,
    /** The release notes, shown before installing. */
    val notes: String?,
)

@Serializable
private data class GhAsset(val name: String = "", val browser_download_url: String = "")

@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val body: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/**
 * Reads the GitHub releases API answer. Only the fields the update screen needs; unknown
 * fields are ignored, so a future API change cannot break the check.
 */
object UpdateChecker {

    /** Repo the releases are published to; the APK is debug-signed and sideloaded. */
    const val LATEST_URL = "https://api.github.com/repos/unclerahman/IPTVWhiz/releases/latest"

    fun parseRelease(json: String): Release? = runCatching {
        val release = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<GhRelease>(json)
        val version = release.tag_name.trim().removePrefix("v")
        if (version.isEmpty()) return null
        Release(
            version = version,
            apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browser_download_url,
            notes = release.body,
        )
    }.getOrNull()

    /**
     * True when [latest] is newer than [current] ("0.7.0" > "0.6.9"; "0.6.6" > "0.6.6-beta").
     * Parsing stops at the first non-numeric part, so a pre-release tag ("0.7.0-beta.1") reads as
     * its base version and never counts as an update past the release it precedes.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0
            if (x != y) return x > y
        }
        return false
    }

    /** Leading numeric parts only: "0.6.6-beta.1" → [0, 6, 6]. */
    private fun numbers(version: String): List<Int> {
        val out = mutableListOf<Int>()
        for (part in version.split('.')) {
            val n = part.toIntOrNull() ?: break
            out += n
        }
        return out
    }
}
