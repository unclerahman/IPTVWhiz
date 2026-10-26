package com.iptvwhiz.app.data.drm

import com.iptvwhiz.app.data.db.Channel

/** The DRM systems IPTV playlists name through Kodi's `#KODIPROP` lines. */
enum class DrmType(val storage: String) {
    CLEAR_KEY("clearkey"),
    WIDEVINE("widevine"),
    PLAYREADY("playready"),
    ;

    companion object {
        fun from(value: String?): DrmType? = when (value?.trim()?.lowercase()) {
            "clearkey", "org.w3.clearkey" -> CLEAR_KEY
            "widevine", "com.widevine.alpha" -> WIDEVINE
            "playready", "com.microsoft.playready" -> PLAYREADY
            else -> null
        }
    }
}

/**
 * What a playlist told us about protecting one channel. Only data from the user's own playlist:
 * the app never extracts keys from a device or bypasses a license server.
 *
 * ClearKey can carry the key itself (Kodi's `kid:key`, or a ready-made JWK response) or a license
 * URL. Widevine and PlayReady always use a license URL; their keys are issued by that server and
 * never appear in a playlist.
 */
data class DrmInfo(
    val type: DrmType,
    /** ClearKey: the W3C "temporary" key response, ready for [androidx.media3.exoplayer.drm.LocalMediaDrmCallback]. */
    val keyResponse: String? = null,
    /** ClearKey/Widevine/PlayReady: a license server URL. */
    val licenseUrl: String? = null,
    /** Extra headers the license server needs (User-Agent, Referer, …). */
    val headers: Map<String, String> = emptyMap(),
) {
    /** True when there is something the player can use. Otherwise the channel is flagged as DRM but unplayable. */
    val isValid: Boolean
        get() = (type == DrmType.CLEAR_KEY && !keyResponse.isNullOrBlank()) || !licenseUrl.isNullOrBlank()

    /** Small print for the error card, never the key itself. */
    val problemHint: String
        get() = when {
            licenseUrl != null -> "${type.storage} license failed"
            keyResponse == null && type == DrmType.CLEAR_KEY -> "no ClearKey key in playlist"
            keyResponse == null -> "${type.storage} needs a license link"
            else -> "${type.storage} key rejected"
        }
}

/** Reads `#KODIPROP` pairs from an M3U playlist, the format Kodi uses. */
object DrmInfoParser {
    private const val LICENSE_TYPE = "inputstream.adaptive.license_type"
    private const val LICENSE_KEY = "inputstream.adaptive.license_key"

    /** True when this `#KODIPROP` line is about DRM. */
    fun isDrmProperty(name: String): Boolean =
        name.trim().lowercase() == LICENSE_TYPE || name.trim().lowercase() == LICENSE_KEY

    fun fromKodiProperties(type: String?, key: String?): DrmInfo? {
        val rawType = type?.trim()?.takeIf { it.isNotEmpty() }
        val rawKey = key?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
        if (rawType == null && rawKey == null) return null

        val drmType = DrmType.from(rawType)
            ?: if (rawType != null) return null else guessType(rawKey)
            ?: return null

        if (rawKey == null) return DrmInfo(drmType)

        val (value, headers) = splitHeaders(rawKey)
        return when (drmType) {
            DrmType.CLEAR_KEY -> when {
                value.isHttpUrl() -> DrmInfo(DrmType.CLEAR_KEY, licenseUrl = value, headers = headers)
                value.startsWith("{") ->
                    DrmInfo(DrmType.CLEAR_KEY, keyResponse = value.takeIf(::looksLikeKeyResponse), headers = headers)
                else ->
                    DrmInfo(DrmType.CLEAR_KEY, keyResponse = clearKeyResponse(value), headers = headers)
            }
            DrmType.WIDEVINE, DrmType.PLAYREADY ->
                DrmInfo(drmType, licenseUrl = value.takeIf { it.isHttpUrl() }, headers = headers)
        }
    }

    /** The database only keeps plain strings, so a view can carry DRM without another table. */
    fun fromStored(type: String?, key: String?, licenseUrl: String?, headers: String?): DrmInfo? {
        val drmType = DrmType.from(type) ?: return null
        return DrmInfo(drmType, key, licenseUrl, parseHeaders(headers))
    }

    /** `Name: Value` lines, as stored in [Channel.drmHeaders]. */
    fun storeHeaders(headers: Map<String, String>): String? =
        headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString("\n") { "${it.key.trim()}: ${it.value.trim()}" }
            .ifEmpty { null }

    private fun parseHeaders(stored: String?): Map<String, String> =
        stored?.lineSequence()
            ?.mapNotNull { line ->
                val at = line.indexOf(':')
                if (at <= 0) null else line.substring(0, at).trim() to line.substring(at + 1).trim()
            }
            ?.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            ?.toMap()
            .orEmpty()

    /** `https://server/path|User-Agent=X&Referer=Y` → URL plus headers. */
    private fun splitHeaders(raw: String): Pair<String, Map<String, String>> {
        val bar = raw.indexOf('|')
        if (bar < 0) return raw.trim() to emptyMap()
        val value = raw.substring(0, bar).trim()
        val headers = raw.substring(bar + 1)
            .split('&', '|')
            .mapNotNull { part ->
                val at = part.indexOf('=')
                if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
            }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            .toMap()
        return value to headers
    }

    private fun guessType(key: String?): DrmType? = when {
        key == null -> null
        key.isHttpUrl() -> DrmType.WIDEVINE
        key.startsWith("{") || key.contains(':') -> DrmType.CLEAR_KEY
        else -> null
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun looksLikeKeyResponse(text: String): Boolean =
        text.contains("\"keys\"") && text.contains("\"kid\"") && text.contains("\"k\"")

    /**
     * Kodi's ClearKey form: `<32 hex key id>:<32 hex key>`. Builds the JSON response
     * Android's ClearKey MediaDrm expects. Null when either part is not a 16-byte hex value.
     */
    fun clearKeyResponse(pair: String): String? {
        val parts = pair.split(':')
        if (parts.size < 2) return null
        val kid = hexToBytes(parts[0]) ?: return null
        val key = hexToBytes(parts[1]) ?: return null
        if (kid.size != 16 || key.size != 16) return null
        return """{"keys":[{"kty":"oct","k":"${base64Url(key)}","kid":"${base64Url(kid)}"}],"type":"temporary"}"""
    }

    private fun hexToBytes(text: String): ByteArray? {
        val value = text.trim().removePrefix("0x").removePrefix("0X")
        if (value.isEmpty() || value.length % 2 != 0) return null
        val out = ByteArray(value.length / 2)
        for (i in out.indices) {
            val high = value[i * 2].digitToIntOrNull(16) ?: return null
            val low = value[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Base64url without padding, as ClearKey JWKs use it. */
    internal fun base64Url(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i + 2 < bytes.size) {
            val n = ((bytes[i].toInt() and 0xff) shl 16) or
                ((bytes[i + 1].toInt() and 0xff) shl 8) or
                (bytes[i + 2].toInt() and 0xff)
            out.append(BASE64_URL[(n ushr 18) and 0x3f])
            out.append(BASE64_URL[(n ushr 12) and 0x3f])
            out.append(BASE64_URL[(n ushr 6) and 0x3f])
            out.append(BASE64_URL[n and 0x3f])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = (bytes[i].toInt() and 0xff) shl 16
                out.append(BASE64_URL[(n ushr 18) and 0x3f])
                out.append(BASE64_URL[(n ushr 12) and 0x3f])
            }
            2 -> {
                val n = ((bytes[i].toInt() and 0xff) shl 16) or ((bytes[i + 1].toInt() and 0xff) shl 8)
                out.append(BASE64_URL[(n ushr 18) and 0x3f])
                out.append(BASE64_URL[(n ushr 12) and 0x3f])
                out.append(BASE64_URL[(n ushr 6) and 0x3f])
            }
        }
        return out.toString()
    }
}

/** DRM for a channel as the playlist and the database carry it. */
val Channel.drmInfo: DrmInfo?
    get() = DrmInfoParser.fromStored(drmType, drmKey, drmLicenseUrl, drmHeaders)
