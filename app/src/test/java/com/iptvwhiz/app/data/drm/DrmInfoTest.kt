package com.iptvwhiz.app.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrmInfoTest {

    @Test
    fun `kodi clear key pair becomes a W3C key response`() {
        val drm = DrmInfoParser.fromKodiProperties(
            "clearkey",
            "feedf00deedeadbeeff0baadf00dd00d:00112233445566778899aabbccddeeff",
        )!!
        assertEquals(DrmType.CLEAR_KEY, drm.type)
        assertEquals(
            """{"keys":[{"kty":"oct","k":"ABEiM0RVZneImaq7zN3u_w","kid":"_u3wDe7erb7v8Lqt8A3QDQ"}],"type":"temporary"}""",
            drm.keyResponse,
        )
        assertTrue(drm.isValid)
        assertNull(drm.licenseUrl)
    }

    @Test
    fun `clear key json from a playlist is passed through`() {
        val json = """{"keys":[{"kty":"oct","k":"ABEiM0RVZneImaq7zN3u_w","kid":"_u3wDe7erb7v8Lqt8A3QDQ"}],"type":"temporary"}"""
        val drm = DrmInfoParser.fromKodiProperties("org.w3.clearkey", json)!!
        assertEquals(DrmType.CLEAR_KEY, drm.type)
        assertEquals(json, drm.keyResponse)
        assertTrue(drm.isValid)
    }

    @Test
    fun `clear key license server and headers`() {
        val drm = DrmInfoParser.fromKodiProperties(
            "clearkey",
            "https://license.example/clear|User-Agent=Box/1.0&Referer=https://example/",
        )!!
        assertEquals(DrmType.CLEAR_KEY, drm.type)
        assertNull(drm.keyResponse)
        assertEquals("https://license.example/clear", drm.licenseUrl)
        assertEquals(mapOf("User-Agent" to "Box/1.0", "Referer" to "https://example/"), drm.headers)
        assertTrue(drm.isValid)
    }

    @Test
    fun `widevine uses the license link and headers`() {
        val drm = DrmInfoParser.fromKodiProperties(
            "com.widevine.alpha",
            "https://widevine.example/license|User-Agent=Kodi/21",
        )!!
        assertEquals(DrmType.WIDEVINE, drm.type)
        assertEquals("https://widevine.example/license", drm.licenseUrl)
        assertEquals("Kodi/21", drm.headers["User-Agent"])
        assertTrue(drm.isValid)
    }

    @Test
    fun `a raw widevine key is not usable`() {
        val drm = DrmInfoParser.fromKodiProperties(
            "com.widevine.alpha",
            "feedf00deedeadbeeff0baadf00dd00d:00112233445566778899aabbccddeeff",
        )!!
        assertEquals(DrmType.WIDEVINE, drm.type)
        assertFalse(drm.isValid)
        assertEquals("widevine needs a license link", drm.problemHint)
    }

    @Test
    fun `a broken clear key pair is flagged instead of played`() {
        val drm = DrmInfoParser.fromKodiProperties("clearkey", "not-a-key")!!
        assertEquals(DrmType.CLEAR_KEY, drm.type)
        assertFalse(drm.isValid)
        assertEquals("no ClearKey key in playlist", drm.problemHint)
    }

    @Test
    fun `missing license type guesses clear key or widevine`() {
        assertEquals(DrmType.CLEAR_KEY, DrmInfoParser.fromKodiProperties(null, "aa".repeat(16) + ":" + "bb".repeat(16))?.type)
        assertEquals(DrmType.WIDEVINE, DrmInfoParser.fromKodiProperties(null, "https://license.example/")?.type)
        assertNull(DrmInfoParser.fromKodiProperties(null, null))
    }

    @Test
    fun `stored headers round trip`() {
        val headers = mapOf("User-Agent" to "VLC/3", "Referer" to "https://ref/")
        val stored = DrmInfoParser.storeHeaders(headers)!!
        val drm = DrmInfoParser.fromStored("clearkey", null, "https://license.example/", stored)!!
        assertEquals(headers, drm.headers)
    }

    @Test
    fun `base64url has no padding`() {
        assertEquals("AA", DrmInfoParser.base64Url(byteArrayOf(0)))
        assertEquals("AAA", DrmInfoParser.base64Url(byteArrayOf(0, 0)))
        assertEquals("AAAA", DrmInfoParser.base64Url(byteArrayOf(0, 0, 0)))
    }
}
