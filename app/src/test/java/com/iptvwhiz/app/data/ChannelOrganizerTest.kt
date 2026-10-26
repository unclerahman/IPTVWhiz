package com.iptvwhiz.app.data

import com.iptvwhiz.app.data.OrganizedPlaylist.Companion.ALL
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.GroupSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOrganizerTest {

    /** A channel as channel_view returns it: provider values with the user's edits already applied. */
    private fun channel(
        number: Int,
        group: String,
        name: String = "Ch $number",
        originalGroup: String = group,
        hidden: Boolean = false,
        favorite: Boolean = false,
        sortOrder: Double = number.toDouble(),
    ) = Channel(
        playlistId = 1, stableKey = "k$number", number = number, name = name, originalName = name,
        logoUrl = null, streamUrl = "http://s/$number", tvgId = null, groupTitle = group,
        originalGroup = originalGroup, userAgent = null, referrer = null, country = null,
        hidden = hidden, favorite = favorite, sortOrder = sortOrder,
    )

    private val news1 = channel(1, "News")
    private val news2 = channel(2, "News")
    private val sport3 = channel(3, "Sport")
    private val sport4 = channel(4, "Sport")
    private val kids5 = channel(5, "Kids")

    private fun names(list: List<Channel>) = list.map { it.number }

    @Test
    fun `blocked channels drop out of lists and counts, empty groups disappear`() {
        val p = OrganizedPlaylist.build(listOf(news1, news2, sport3, sport4, kids5), emptyList())
        val blocked = setOf(news2.ref, sport3.ref, sport4.ref)
        val watching = p.without(blocked)
        assertEquals(listOf(1, 5), names(watching.browse(ALL)))
        assertEquals(listOf("News", "Kids"), watching.visibleGroups.map { it.key })
        assertEquals(1, watching.visibleGroups.first { it.key == "News" }.visibleCount)
        assertEquals(2, watching.visibleCount)
        // The full playlist (manage screens) is untouched.
        assertEquals(5, p.visibleCount)
        assertEquals(p, p.without(emptySet()))
    }

    @Test
    fun `provider order when nothing is edited`() {
        val p = OrganizedPlaylist.build(listOf(sport3, news1, kids5, news2, sport4), emptyList())
        assertEquals(listOf("News", "Sport", "Kids"), p.groups.map { it.key })
        assertEquals(listOf(1, 2, 3, 4, 5), names(p.browse(ALL)))
        assertEquals(listOf(3, 4), names(p.browse("Sport")))
    }

    @Test
    fun `hidden channels leave browsing but stay in manage`() {
        val hidden = news2.copy(hidden = true)
        val p = OrganizedPlaylist.build(listOf(news1, hidden, sport3), emptyList())
        assertEquals(listOf(1, 3), names(p.browse(ALL)))
        assertEquals(listOf(1), names(p.browse("News")))
        assertEquals(listOf(1, 2), names(p.manage("News")))
        assertEquals(1, p.groups.first { it.key == "News" }.visibleCount)
        assertEquals(2, p.visibleCount)
    }

    @Test
    fun `hidden group leaves All and the group list but keeps its channels editable`() {
        val p = OrganizedPlaylist.build(
            listOf(news1, sport3, sport4),
            listOf(GroupSetting(1, "Sport", hidden = true)),
        )
        assertEquals(listOf("News"), p.visibleGroups.map { it.key })
        assertEquals(listOf(1), names(p.browse(ALL)))
        assertEquals(listOf(3, 4), names(p.manage("Sport")))
        assertTrue(p.groups.first { it.key == "Sport" }.hidden)
    }

    @Test
    fun `renamed group keeps its key`() {
        val p = OrganizedPlaylist.build(listOf(news1), listOf(GroupSetting(1, "News", customName = "World news")))
        val group = p.groups.single()
        assertEquals("News", group.key)
        assertEquals("World news", group.displayName)
    }

    @Test
    fun `custom group shows even when empty, but not for browsing`() {
        val p = OrganizedPlaylist.build(listOf(news1), listOf(GroupSetting(1, "Mine", isCustom = true, sortOrder = 10.0)))
        assertEquals(listOf("News", "Mine"), p.groups.map { it.key })
        assertEquals(listOf("News"), p.visibleGroups.map { it.key })
    }

    @Test
    fun `channel moved to a custom group`() {
        val moved = sport3.copy(groupTitle = "Mine", sortOrder = 1.0)
        val p = OrganizedPlaylist.build(
            listOf(news1, moved, sport4),
            listOf(GroupSetting(1, "Mine", isCustom = true, sortOrder = 0.5)),
        )
        assertEquals(listOf("Mine", "News", "Sport"), p.groups.map { it.key })
        assertEquals(listOf(3), names(p.browse("Mine")))
        assertEquals(listOf(4), names(p.browse("Sport")))
        assertEquals(listOf(3, 1, 4), names(p.browse(ALL)))
    }

    @Test
    fun `custom channel and group order`() {
        val p = OrganizedPlaylist.build(
            listOf(news1, news2.copy(sortOrder = 0.5), sport3, sport4),
            listOf(GroupSetting(1, "Sport", sortOrder = 0.0)),
        )
        assertEquals(listOf("Sport", "News"), p.groups.map { it.key })
        assertEquals(listOf(2, 1), names(p.browse("News")))
        assertEquals(listOf(3, 4, 2, 1), names(p.browse(ALL)))
    }

    @Test
    fun `watchlist keys round-trip and never clash with groups`() {
        val key = OrganizedPlaylist.watchlistKey(42)
        assertEquals(42L, OrganizedPlaylist.watchlistId(key))
        assertNull(OrganizedPlaylist.watchlistId("News"))
        assertNull(OrganizedPlaylist.watchlistId(ALL))
    }

    @Test
    fun `positions between neighbours`() {
        assertEquals(0.0, Positions.between(null, null)!!, 0.0)
        assertEquals(4.0, Positions.between(null, 5.0)!!, 0.0)
        assertEquals(6.0, Positions.between(5.0, null)!!, 0.0)
        assertEquals(5.5, Positions.between(5.0, 6.0)!!, 0.0)
        assertNull(Positions.between(5.0, 5.0 + 1e-9))
    }

    @Test
    fun `repeated moves into the same gap eventually ask for renumbering`() {
        var before = 1.0
        val after = 2.0
        var steps = 0
        while (true) {
            before = Positions.between(before, after) ?: break
            steps++
        }
        // Plenty of moves before a renumber is needed, and it does terminate.
        assertTrue(steps in 15..60)
    }
}
