package com.iptvwhiz.app.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class M3uParserTest {

    private fun parse(text: String) = M3uParser.parse(text.reader().buffered())

    @Test
    fun `parses attributes, title and url`() {
        val result = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://x/logo.png" group-title="UK",BBC One HD
            http://example.com/bbc1.m3u8
            """.trimIndent(),
        )
        val entry = result.entries.single()
        assertEquals("BBC One HD", entry.name)
        assertEquals("http://example.com/bbc1.m3u8", entry.url)
        assertEquals("bbc1.uk", entry.tvgId)
        assertEquals("BBC One", entry.tvgName)
        assertEquals("http://x/logo.png", entry.logo)
        assertEquals("UK", entry.group)
    }

    @Test
    fun `comma inside quoted attribute does not end the attributes`() {
        val entry = parse(
            "#EXTM3U\n#EXTINF:-1 group-title=\"News, World\",Name, with comma\nhttp://a/b\n",
        ).entries.single()
        assertEquals("News, World", entry.group)
        assertEquals("Name, with comma", entry.name)
    }

    @Test
    fun `escaped quotes and unquoted values`() {
        val entry = parse(
            "#EXTINF:-1 tvg-name=\"Quote \\\"Test\\\", Inc\" tvg-id=abc.us,Chan\nhttp://a/b\n",
        ).entries.single()
        assertEquals("Quote \"Test\", Inc", entry.tvgName)
        assertEquals("abc.us", entry.tvgId)
    }

    @Test
    fun `header epg urls including comma separated list`() {
        val result = parse(
            "#EXTM3U url-tvg=\"http://a/epg.xml\" x-tvg-url=\"http://b/1.xml.gz, http://c/2.xml.gz\"\n",
        )
        assertEquals(listOf("http://a/epg.xml", "http://b/1.xml.gz", "http://c/2.xml.gz"), result.epgUrls)
    }

    @Test
    fun `BOM, CRLF, EXTGRP and EXTVLCOPT`() {
        val entry = parse(
            "﻿#EXTM3U\r\n#EXTINF:-1,Sports One\r\n#EXTGRP:Sports\r\n" +
                "#EXTVLCOPT:http-user-agent=VLC/3.0\r\n#EXTVLCOPT:http-referrer=http://ref/\r\nhttp://a/s1\r\n",
        ).entries.single()
        assertEquals("Sports One", entry.name)
        assertEquals("Sports", entry.group)
        assertEquals("VLC/3.0", entry.userAgent)
        assertEquals("http://ref/", entry.referrer)
    }

    @Test
    fun `options do not leak to the next channel`() {
        val entries = parse(
            "#EXTINF:-1,A\n#EXTVLCOPT:http-user-agent=UA\nhttp://a\n#EXTINF:-1,B\nhttp://b\n",
        ).entries
        assertEquals("UA", entries[0].userAgent)
        assertNull(entries[1].userAgent)
    }

    @Test
    fun `bare url list without EXTINF`() {
        val entries = parse("http://host/live/one.m3u8\nhttp://host/two.ts?token=1\n").entries
        assertEquals(listOf("one.m3u8", "two.ts"), entries.map { it.name })
    }

    @Test
    fun `missing title falls back to tvg-name`() {
        val entry = parse("#EXTINF:-1 tvg-name=\"Fallback\",\nhttp://a\n").entries.single()
        assertEquals("Fallback", entry.name)
    }

    @Test
    fun `parses the project test playlist`() {
        val file = File("../testdata/test-playlist.m3u")
        val result = file.bufferedReader().use { M3uParser.parse(it) }
        assertEquals(6, result.entries.size)
        assertEquals(listOf("https://epg.kanya.io/us/guide.xml.gz"), result.epgUrls)
        assertEquals("IPTVWhiz/0.1", result.entries[4].userAgent)
        assertEquals("Parser, Edge Cases", result.entries[4].group)
        assertNull(result.entries[5].group)
    }

    @Test
    fun `reports progress`() {
        val text = buildString { repeat(1200) { append("#EXTINF:-1,C$it\nhttp://a/$it\n") } }
        val reported = mutableListOf<Int>()
        M3uParser.parse(text.reader().buffered()) { reported += it }
        assertEquals(listOf(500, 1000, 1200), reported)
    }

    @Test
    fun `a web page is not read as channels`() {
        val page = "<!DOCTYPE html>\n<html>\n<head>\n<title>playlist.m3u - GitHub</title>\n</head>\n<body>Not found</body>\n</html>\n"
        assertEquals(0, parse(page).entries.size)
    }

    @Test
    fun `stray text between EXTINF and link is skipped`() {
        val entry = parse("#EXTM3U\n#EXTINF:-1,News\nsome junk line\nrtmp://host/live/news\n").entries.single()
        assertEquals("News", entry.name)
        assertEquals("rtmp://host/live/news", entry.url)
    }
}
