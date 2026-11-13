package com.iptvwhiz.app.data.epg

import com.iptvwhiz.app.data.db.ProgramSlot

/**
 * One block in the timeline guide: a programme, or a gap with no information ([slot] null).
 * [start] and [stop] are clipped to the visible window, so the first and last blocks of a row may
 * be shorter than the programme itself.
 */
data class GuideBlock(val start: Long, val stop: Long, val slot: ProgramSlot?) {
    val minutes: Float get() = (stop - start) / 60_000f
    fun isOn(at: Long) = at in start until stop
}

/** Pure layout rules for the scrollable guide (PLAN.md §23), kept here so they can be tested. */
object GuideTimeline {
    const val HALF_HOUR = 30 * 60_000L

    /** Gaps shorter than this are absorbed into the programme before them instead of showing "No information". */
    private const val MIN_GAP = 60_000L

    /** The guide starts at the half hour before [now], so the programme on air is always in view. */
    fun windowStart(now: Long): Long = now - now.mod(HALF_HOUR)

    /** Half-hour marks from [from] (inclusive) to [until] (exclusive), for the time ruler. */
    fun ticks(from: Long, until: Long): List<Long> =
        generateSequence(from - from.mod(HALF_HOUR)) { it + HALF_HOUR }
            .dropWhile { it < from }
            .takeWhile { it < until }
            .toList()

    /**
     * A channel's row from [from] to [until]: programmes in time order, overlaps trimmed (the
     * earlier programme wins), and gaps filled with empty blocks. Covers the whole window, so every
     * row has the same width.
     */
    fun blocks(slots: List<ProgramSlot>, from: Long, until: Long): List<GuideBlock> {
        if (until <= from) return emptyList()
        val out = mutableListOf<GuideBlock>()
        var cursor = from
        for (slot in slots.sortedBy { it.start }) {
            val start = maxOf(slot.start, cursor)
            val stop = minOf(slot.stop, until)
            if (stop <= start) continue
            if (start - cursor >= MIN_GAP) {
                out += GuideBlock(cursor, start, null)
            } else if (start > cursor && out.isNotEmpty()) {
                out[out.lastIndex] = out.last().copy(stop = start)
            }
            // A tiny gap at the very start of the window just widens the first programme.
            val begin = if (out.isEmpty()) cursor else start
            out += GuideBlock(begin, stop, slot)
            cursor = stop
            if (cursor >= until) break
        }
        if (cursor < until) {
            if (until - cursor < MIN_GAP && out.isNotEmpty()) {
                out[out.lastIndex] = out.last().copy(stop = until)
            } else {
                out += GuideBlock(cursor, until, null)
            }
        }
        return out
    }
}
