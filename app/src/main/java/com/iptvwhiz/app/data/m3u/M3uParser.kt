package com.iptvwhiz.app.data.m3u

import com.iptvwhiz.app.data.drm.DrmInfo
import com.iptvwhiz.app.data.drm.DrmInfoParser
import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val country: String? = null,
    /** ClearKey/Widevine/PlayReady data from Kodi `#KODIPROP` lines, when the playlist has any. */
    val drm: DrmInfo? = null,
)

data class M3uPlaylist(
    /** EPG sources from the header (`url-tvg`, `x-tvg-url`, `tvg-url`), comma-separated lists split out. */
    val epgUrls: List<String>,
    val entries: List<M3uEntry>,
)

/**
 * Streaming, forgiving M3U/M3U8 parser. Reads line by line so big playlists never sit in memory as text.
 * Handles: BOM, CRLF, `#EXTINF` attributes (quoted, unquoted, escaped quotes), commas inside quotes,
 * `#EXTGRP`, `#EXTVLCOPT` user agent / referrer, and bare URL lists without `#EXTINF`.
 */
object M3uParser {

    /** A stream link: any scheme (http, https, rtmp, rtsp, udp...) followed by "://". */
    private val linkRegex = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://\S+$""")

    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)=(?:"((?:[^"\\]|\\.)*)"|([^\s",]+))""")

    fun parse(reader: BufferedReader, onProgress: ((Int) -> Unit)? = null): M3uPlaylist {
        val epgUrls = mutableListOf<String>()
        val entries = mutableListOf<M3uEntry>()

        var info: ExtInf? = null
        var extGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null
        var licenseType: String? = null
        var licenseKey: String? = null

        reader.lineSequence().forEach { raw ->
            val line = raw.removePrefix("﻿").trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter("#EXTM3U"))
                    listOf("url-tvg", "x-tvg-url", "tvg-url").forEach { key ->
                        attrs[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let(epgUrls::addAll)
                    }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = parseExtInf(line.substringAfter(':', ""))
                    extGroup = null
                    userAgent = null
                    referrer = null
                    // DRM lines are deliberately kept: Kodi applies a #KODIPROP block to the next
                    // stream link, and some providers put the block *before* #EXTINF. They are
                    // cleared once a channel is emitted, so they cannot leak further down.
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    extGroup = line.substringAfter(':').trim().ifEmpty { null }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    // Kodi puts DRM details here, usually between #EXTINF and the stream link, but
                    // some playlists put them before #EXTINF. Either way they belong to the next link.
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().ifEmpty { null }
                    if (DrmInfoParser.isDrmProperty(key)) {
                        if (key == "inputstream.adaptive.license_type") licenseType = value
                        if (key == "inputstream.adaptive.license_key") licenseKey = value
                    }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().ifEmpty { null }
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                // Anything else that isn't a link (e.g. the lines of a web page) is skipped, so a page
                // opened by mistake reads as "not a playlist" instead of channels named "<html>".
                !linkRegex.matches(line) -> Unit
                else -> {
                    val current = info
                    val attrs = current?.attributes.orEmpty()
                    val name = current?.title?.takeIf { it.isNotBlank() }
                        ?: attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        ?: nameFromUrl(line)
                    entries += M3uEntry(
                        name = name,
                        url = line,
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup,
                        userAgent = userAgent ?: attrs["user-agent"] ?: attrs["http-user-agent"],
                        referrer = referrer ?: attrs["http-referrer"],
                        country = attrs["tvg-country"]?.takeIf { it.isNotBlank() },
                        drm = DrmInfoParser.fromKodiProperties(licenseType, licenseKey),
                    )
                    info = null
                    extGroup = null
                    userAgent = null
                    referrer = null
                    licenseType = null
                    licenseKey = null
                    if (onProgress != null && entries.size % 500 == 0) onProgress(entries.size)
                }
            }
        }
        onProgress?.invoke(entries.size)
        return M3uPlaylist(epgUrls.distinct(), entries)
    }

    private class ExtInf(val attributes: Map<String, String>, val title: String)

    /** `-1 tvg-id="x" group-title="A, B",Channel name` → attributes + title. The title starts after the first unquoted comma. */
    private fun parseExtInf(body: String): ExtInf {
        var inQuotes = false
        var split = -1
        for (i in body.indices) {
            val c = body[i]
            if (c == '"' && (i == 0 || body[i - 1] != '\\')) inQuotes = !inQuotes
            if (c == ',' && !inQuotes) {
                split = i
                break
            }
        }
        val attrPart = if (split >= 0) body.substring(0, split) else body
        val title = if (split >= 0) body.substring(split + 1).trim() else ""
        return ExtInf(parseAttributes(attrPart), title)
    }

    private fun parseAttributes(text: String): Map<String, String> =
        attributeRegex.findAll(text).associate { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groups[2]?.value?.replace("\\\"", "\"") ?: match.groupValues[3]
            key to value.trim()
        }

    private fun nameFromUrl(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { url }
}
