package com.iptvwhiz.app.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrmDiagnosticsTest {

    private val tv3Key =
        """{ "keys":[ { "kty":"oct", "k":"X842T7xEmYVll7GalvRGSA", "kid":"O8Pw5Riu2S6AqYEY5bwsEA" } ], "type":"temporary" }"""

    private val clearKey = DrmInfo(type = DrmType.CLEAR_KEY, keyResponse = tv3Key)

    @Test
    fun `key ids are read from a response without the key`() {
        assertEquals(setOf("O8Pw5Riu2S6AqYEY5bwsEA"), clearKey.clearKeyKeyIds)
        assertTrue(clearKey.clearKeyKeyIds.none { it.contains("X842") })
    }

    @Test
    fun `key ids are read from the device's key request`() {
        val request = """{"kids":["O8Pw5Riu2S6AqYEY5bwsEA"],"type":"temporary"}"""
        assertEquals(setOf("O8Pw5Riu2S6AqYEY5bwsEA"), DrmKeyIds.fromKeyRequest(request))
    }

    @Test
    fun `padding and the standard alphabet compare equal`() {
        assertEquals(
            setOf("-_8"),
            DrmKeyIds.fromKeyRequest("""{"kids":["+/8="],"type":"temporary"}"""),
        )
    }

    @Test
    fun `a stale provider key is named as a key id mismatch`() {
        val detail = drmFailureDetail(
            drm = clearKey,
            requestedKeyIds = setOf("AAAAAAAAAAAAAAAAAAAAAA"),
        )
        assertEquals("clearkey · the playlist key doesn't match this stream's key ID", detail)
    }

    @Test
    fun `a matching key id is not reported as a mismatch`() {
        val detail = drmFailureDetail(
            drm = clearKey,
            requestedKeyIds = setOf("O8Pw5Riu2S6AqYEY5bwsEA"),
        )
        assertEquals("clearkey · the device rejected the playlist key", detail)
    }

    @Test
    fun `a refused license shows the server status`() {
        val drm = DrmInfo(type = DrmType.WIDEVINE, licenseUrl = "https://license.example/widevine")
        assertEquals(
            "widevine · the license server said no (HTTP 403)",
            drmFailureDetail(drm, httpStatus = 403),
        )
        assertEquals("widevine · the license request failed", drmFailureDetail(drm))
    }

    @Test
    fun `a device without the module says so`() {
        val drm = DrmInfo(type = DrmType.WIDEVINE, licenseUrl = "https://license.example/widevine")
        assertEquals(
            "widevine · this device has no widevine module",
            drmFailureDetail(drm, deviceUnsupported = true),
        )
    }

    @Test
    fun `a stream that does not offer the system says so`() {
        assertEquals(
            "playready · the stream doesn't offer playready",
            drmFailureDetail(
                DrmInfo(type = DrmType.PLAYREADY, licenseUrl = "https://license.example/pr"),
                streamLacksScheme = true,
            ),
        )
    }

    @Test
    fun `key id matching only applies to clear key`() {
        val widevine = DrmInfo(type = DrmType.WIDEVINE, licenseUrl = "https://license.example/wv")
        assertTrue(widevine.clearKeyMatches(setOf("anything")))
        assertTrue(clearKey.clearKeyMatches(emptySet()))
        assertTrue(clearKey.clearKeyMatches(setOf("O8Pw5Riu2S6AqYEY5bwsEA")))
        assertFalse(clearKey.clearKeyMatches(setOf("AAAAAAAAAAAAAAAAAAAAAA")))
    }

    @Test
    fun `a protected stream with no drm lines explains itself`() {
        assertEquals(
            "the stream is copy-protected, and the playlist has no key for it",
            drmFailureDetail(drm = null),
        )
    }
}
