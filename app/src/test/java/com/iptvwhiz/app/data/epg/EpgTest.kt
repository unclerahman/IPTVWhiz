package com.iptvwhiz.app.data.epg

import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.ProgramSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class EpgTest {

    @Test
    fun `xmltv times with and without offset`() {
        // 2026-10-25 12:00:00 UTC
        val utcNoon = 1_792_929_600_000L
        assertEquals(utcNoon, parseXmltvTime("20261025120000 +0000"))
        assertEquals(utcNoon, parseXmltvTime("20261025140000 +0200"))
        assertEquals(utcNoon, parseXmltvTime("20261025070000 -0500"))
        assertEquals(utcNoon, parseXmltvTime("20261025120000"))
        assertEquals(utcNoon, parseXmltvTime("202610251200"))
        assertEquals(951_782_400_000L, parseXmltvTime("20000229000000 +0000")) // leap day
        assertNull(parseXmltvTime("nonsense"))
        assertNull(parseXmltvTime("20261399120000"))
    }

    @Test
    fun `parses channels and programmes, first title wins, bad programmes skipped`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="test">
              <channel id="bbc1.uk"><display-name>BBC One</display-name><display-name>BBC 1 HD</display-name><icon src="x"/></channel>
              <programme start="20261025120000 +0000" stop="20261025130000 +0000" channel="bbc1.uk">
                <title lang="en">News at Noon</title><title lang="cy">Newyddion</title>
                <desc lang="en">Headlines &amp; weather.</desc>
                <category>News</category>
              </programme>
              <programme start="20261025130000 +0000" stop="20261025130000 +0000" channel="bbc1.uk"><title>Zero length</title></programme>
              <programme start="20261025140000 +0000" stop="20261025150000 +0000" channel="bbc1.uk"></programme>
              <programme start="20261025150000 +0000" stop="20261025160000 +0000" channel="bbc1.uk"><title><![CDATA[Film: "Up"]]></title></programme>
            </tv>
        """.trimIndent()
        val channels = mutableListOf<Pair<String, List<String>>>()
        val programmes = mutableListOf<XmltvProgramme>()
        XmltvParser(KXmlParser()).parse(xml.byteInputStream(), { id, names -> channels += id to names }, { programmes += it })

        assertEquals(listOf("bbc1.uk" to listOf("BBC One", "BBC 1 HD")), channels)
        assertEquals(listOf("News at Noon", "Film: \"Up\""), programmes.map { it.title })
        assertEquals("Headlines & weather.", programmes[0].description)
        assertEquals(3_600_000L, programmes[0].stop - programmes[0].start)
    }

    @Test
    fun `name keys ignore case, symbols, brackets and quality suffixes`() {
        assertEquals("bbcone", epgNameKey("BBC One HD"))
        assertEquals("bbcone", epgNameKey("bbc one (UK) [FHD]"))
        assertEquals("news24", epgNameKey("News 24 Ⓢ"))
        assertEquals("canal4", epgNameKey("Canal 4"))
    }

    private fun channel(key: String, name: String, tvgId: String? = null) = Channel(
        playlistId = 1, stableKey = key, number = 1, name = name, originalName = name, logoUrl = null,
        streamUrl = "", tvgId = tvgId, groupTitle = "G", originalGroup = "G", userAgent = null, referrer = null,
        hidden = false, favorite = false, sortOrder = 1.0,
    )

    @Test
    fun `matcher prefers tvg-id, falls back to names`() {
        val matcher = EpgMatcher(
            listOf(
                channel("a", "BBC One", tvgId = "BBC1.uk"),
                channel("b", "ITV HD"),
                channel("c", "Channel With Wrong Id", tvgId = "missing.id"),
            ),
        )
        matcher.addGuideChannel("bbc1.uk", listOf("BBC One"))
        matcher.addGuideChannel("bbc1.other", listOf("BBC One"))
        matcher.addGuideChannel("itv.uk", listOf("ITV"))
        matcher.addGuideChannel("x.wrong", listOf("Channel with wrong id"))

        assertTrue(matcher.wants("bbc1.uk")) // case-insensitive tvg-id
        assertFalse(matcher.wants("bbc1.other")) // name matches, but the channel's tvg-id already claimed it
        assertTrue(matcher.wants("itv.uk")) // by name, "HD" ignored
        assertTrue(matcher.wants("x.wrong")) // tvg-id not in the guide, so the name may match
        assertFalse(matcher.wants("unknown"))
        assertEquals(mapOf("a" to "bbc1.uk", "b" to "itv.uk", "c" to "x.wrong"), matcher.mapping)
    }

    @Test
    fun `now and next`() {
        val now = 10_000L
        val slots = listOf(
            ProgramSlot("a", 5_000, 12_000, "Current", null),
            ProgramSlot("a", 12_000, 15_000, "Following", null),
            ProgramSlot("a", 15_000, 18_000, "Later", null),
            ProgramSlot("b", 11_000, 13_000, "Starts soon", null),
        )
        val result = EpgRepository.nowNext(slots, now)
        assertEquals("Current", result.getValue("a").now?.title)
        assertEquals("Following", result.getValue("a").next?.title)
        assertNull(result.getValue("b").now)
        assertEquals("Starts soon", result.getValue("b").next?.title)
        assertEquals(5f / 7f, result.getValue("a").progress(now), 0.001f)
    }

    @Test
    fun `many guide links are capped, matching countries first`() {
        val links = (1..30).map { "https://epg.example/epg_ripper_C${it}1.xml.gz" } +
            listOf("https://epg.example/epg_ripper_AL1.xml.gz", "https://epg.example/epg_ripper_US1.xml.gz")
        val channels = listOf(
            channel("a", "A", tvgId = "Kanali7.al"),
            channel("b", "B", tvgId = "Top.al"),
            channel("c", "C", tvgId = "Cnn.us@SD"),
        )
        val chosen = EpgRepository.selectLinks(links, channels)
        assertEquals(EpgRepository.MAX_LINKS, chosen.size)
        assertTrue(chosen.containsAll(listOf(links[30], links[31])))
        // Few links: all used, order kept.
        assertEquals(links.take(3), EpgRepository.selectLinks(links.take(3), channels))
    }
}
