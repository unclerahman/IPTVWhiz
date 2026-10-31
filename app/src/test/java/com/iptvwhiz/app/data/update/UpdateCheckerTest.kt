package com.iptvwhiz.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `reads version apk link and notes from a release`() {
        val release = UpdateChecker.parseRelease(
            """
            {
              "tag_name": "v0.6.6",
              "body": "**Fixed**\n- FAST channels play again.",
              "assets": [
                {"name": "source.zip", "browser_download_url": "https://x/source.zip"},
                {"name": "IPTVWhiz-0.6.6.apk", "browser_download_url": "https://x/IPTVWhiz-0.6.6.apk"}
              ],
              "prerelease": false
            }
            """.trimIndent(),
        )!!
        assertEquals("0.6.6", release.version)
        assertEquals("https://x/IPTVWhiz-0.6.6.apk", release.apkUrl)
        assertTrue(release.notes!!.contains("FAST channels"))
    }

    @Test
    fun `a release without an apk asset still parses`() {
        val release = UpdateChecker.parseRelease("""{"tag_name":"v0.7.0","assets":[]}""")!!
        assertEquals("0.7.0", release.version)
        assertNull(release.apkUrl)
    }

    @Test
    fun `broken json and empty tags are rejected`() {
        assertNull(UpdateChecker.parseRelease("not json"))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":""}"""))
        assertNull(UpdateChecker.parseRelease("""{"assets":[]}"""))
    }

    @Test
    fun `version comparison decides updates`() {
        assertTrue(UpdateChecker.isNewer("0.6.6", "0.6.5"))
        assertTrue(UpdateChecker.isNewer("0.7.0", "0.6.9"))
        assertTrue(UpdateChecker.isNewer("0.6.10", "0.6.9"))
        assertFalse(UpdateChecker.isNewer("0.6.6", "0.6.6"))
        assertFalse(UpdateChecker.isNewer("0.6.5", "0.6.6"))
        // A pre-release tag must never read as newer than the release it precedes.
        assertFalse(UpdateChecker.isNewer("0.6.6-beta.1", "0.6.6"))
        assertFalse(UpdateChecker.isNewer("v0.7", "0.7.0"))
    }
}
