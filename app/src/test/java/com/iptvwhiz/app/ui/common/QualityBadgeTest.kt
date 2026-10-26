package com.iptvwhiz.app.ui.common

import com.iptvwhiz.app.player.StreamStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The text of the quality badge over the video. */
class QualityBadgeTest {
    private lateinit var original: Locale

    @Before
    fun useUsLocale() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `summary shows resolution, fps and bitrate`() {
        assertEquals(
            "1920×1080 · 25 fps · 4.2 Mbps",
            StreamStats(width = 1920, height = 1080, fps = 25f, mbps = 4.2f).summary(),
        )
    }

    @Test
    fun `summary is empty until a number arrives`() {
        assertNull(StreamStats().summary())
    }

    @Test
    fun `summary skips parts that are not known yet`() {
        assertEquals("1920×1080", StreamStats(width = 1920, height = 1080).summary())
        assertEquals("25 fps", StreamStats(fps = 25f).summary())
    }
}
