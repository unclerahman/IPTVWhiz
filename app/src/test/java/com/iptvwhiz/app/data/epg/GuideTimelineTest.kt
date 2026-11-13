package com.iptvwhiz.app.data.epg

import com.iptvwhiz.app.data.db.ProgramSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTimelineTest {
    private val min = 60_000L

    private fun slot(start: Long, stop: Long, title: String) = ProgramSlot("a", start * min, stop * min, title, null)

    private fun shape(blocks: List<GuideBlock>) = blocks.map { Triple(it.start / min, it.stop / min, it.slot?.title) }

    @Test
    fun `window starts at the half hour before now`() {
        assertEquals(30 * min, GuideTimeline.windowStart(47 * min))
        assertEquals(60 * min, GuideTimeline.windowStart(60 * min))
        assertEquals(listOf(30 * min, 60 * min, 90 * min), GuideTimeline.ticks(30 * min, 120 * min))
        assertEquals(listOf(30 * min), GuideTimeline.ticks(10 * min, 31 * min))
    }

    @Test
    fun `programmes are clipped to the window and gaps say no information`() {
        val blocks = GuideTimeline.blocks(
            listOf(slot(0, 50, "Started earlier"), slot(50, 80, "Next"), slot(100, 200, "Runs past the end")),
            from = 30 * min,
            until = 150 * min,
        )
        assertEquals(
            listOf(
                Triple(30L, 50L, "Started earlier"),
                Triple(50L, 80L, "Next"),
                Triple(80L, 100L, null),
                Triple(100L, 150L, "Runs past the end"),
            ),
            shape(blocks),
        )
    }

    @Test
    fun `every row covers the whole window, even with no programmes`() {
        assertEquals(listOf(Triple(0L, 60L, null)), shape(GuideTimeline.blocks(emptyList(), 0, 60 * min)))
        val partial = GuideTimeline.blocks(listOf(slot(10, 20, "Only")), 0, 60 * min)
        assertEquals(0L, partial.first().start)
        assertEquals(60 * min, partial.last().stop)
        assertNull(partial.last().slot)
    }

    @Test
    fun `overlaps are trimmed and tiny gaps absorbed`() {
        val blocks = GuideTimeline.blocks(
            listOf(slot(0, 40, "A"), slot(30, 60, "B overlaps A"), slot(60, 90, "C")),
            from = 0,
            until = 90 * min,
        )
        assertEquals(listOf(Triple(0L, 40L, "A"), Triple(40L, 60L, "B overlaps A"), Triple(60L, 90L, "C")), shape(blocks))

        // Thirty seconds between two programmes: no sliver of "No information".
        val seconds = GuideTimeline.blocks(
            listOf(
                ProgramSlot("a", 0, 30 * min, "X", null),
                ProgramSlot("a", 30 * min + 30_000, 60 * min, "Y", null),
            ),
            from = 0,
            until = 60 * min,
        )
        assertEquals(listOf("X", "Y"), seconds.map { it.slot?.title })
        assertEquals(30 * min + 30_000, seconds[0].stop)
        assertTrue(seconds.zipWithNext().all { (a, b) -> a.stop == b.start })
    }

    @Test
    fun `isOn marks the programme on air`() {
        val block = GuideBlock(10 * min, 20 * min, null)
        assertTrue(block.isOn(10 * min))
        assertTrue(!block.isOn(20 * min))
        assertEquals(10f, block.minutes)
    }
}
