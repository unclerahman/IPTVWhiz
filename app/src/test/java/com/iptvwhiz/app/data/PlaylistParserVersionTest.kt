package com.iptvwhiz.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The upgrade path for stored channels: a playlist imported by a version that changed the parser
 * has to be re-imported once, because its channels can be missing data (for example a DRM key).
 */
class PlaylistParserVersionTest {

    @Test
    fun `channels with no recorded parser version are re-imported`() {
        assertTrue(PlaylistRepository.needsReimport(0))
    }

    @Test
    fun `channels from an older parser are re-imported`() {
        assertTrue(PlaylistRepository.needsReimport(PlaylistRepository.PARSER_VERSION - 1))
    }

    @Test
    fun `channels from the current parser are left alone`() {
        assertFalse(PlaylistRepository.needsReimport(PlaylistRepository.PARSER_VERSION))
        assertFalse(PlaylistRepository.needsReimport(PlaylistRepository.PARSER_VERSION + 1))
    }
}
