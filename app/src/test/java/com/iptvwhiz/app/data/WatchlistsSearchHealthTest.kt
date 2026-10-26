package com.iptvwhiz.app.data

import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.data.db.WatchlistEntity
import com.iptvwhiz.app.data.db.WatchlistItem
import com.iptvwhiz.app.data.db.effectiveCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistsSearchHealthTest {

    private fun channel(
        playlistId: Long,
        key: String,
        name: String,
        group: String = "G",
        tvgId: String? = null,
        country: String? = null,
        hidden: Boolean = false,
        number: Int = 1,
    ) = Channel(
        playlistId = playlistId, stableKey = key, number = number, name = name, originalName = name, logoUrl = null,
        streamUrl = "http://s/$key", tvgId = tvgId, groupTitle = group, originalGroup = group, userAgent = null,
        referrer = null, country = country, hidden = hidden, favorite = false, sortOrder = number.toDouble(),
    )

    @Test
    fun `watchlists mix playlists, keep order and keep missing channels`() {
        val a = channel(1, "a", "Alpha")
        val b = channel(2, "b", "Beta")
        val lists = listOf(
            WatchlistEntity(id = 2, name = "Sports", position = 1.0),
            WatchlistEntity(id = 1, name = "Favorites", builtIn = true, position = 0.0),
        )
        val items = listOf(
            WatchlistItem(2, 2, "b", position = 1.0),
            WatchlistItem(2, 1, "a", position = 2.0),
            WatchlistItem(2, 1, "gone", position = 3.0),
        )
        val playlists = listOf(
            PlaylistEntity(id = 1, name = "Provider A", type = PlaylistType.M3U_URL, source = "x"),
            PlaylistEntity(id = 2, name = "Provider B", type = PlaylistType.M3U_URL, source = "y"),
        )
        val built = Watchlists.build(lists, items, listOf(a, b), playlists)
        assertEquals(listOf(1L, 2L), built.map { it.id }) // Favorites first by position
        val sports = built[1]
        assertEquals(listOf("Beta", "Alpha"), sports.available.map { it.name })
        assertEquals(3, sports.entries.size)
        assertNull(sports.entries[2].channel) // missing after a refresh, still listed
        assertEquals("Provider B", sports.entries[0].playlistName)
        assertTrue(sports.contains(ChannelRef(1, "a")))
    }

    @Test
    fun `country from tvg-country, else from the tvg-id suffix`() {
        assertEquals("AL", channel(1, "k", "K", tvgId = "Kanali7.al").effectiveCountry)
        assertEquals("US", channel(1, "k", "K", tvgId = "CNN.us@SD").effectiveCountry)
        assertEquals("FR", channel(1, "k", "K", tvgId = "x.us", country = "fr").effectiveCountry)
        assertNull(channel(1, "k", "K", tvgId = "test.bipbop").effectiveCountry)
        assertNull(channel(1, "k", "K").effectiveCountry)
    }

    @Test
    fun `search by words, accents, country, group and playlist`() {
        val channels = listOf(
            channel(1, "1", "Télé-Québec", group = "Canada", tvgId = "tq.ca", number = 1),
            channel(1, "2", "BBC News", group = "UK", tvgId = "bbcnews.uk", number = 2),
            channel(2, "3", "News 24", group = "News", tvgId = "news24.al", number = 1),
            channel(2, "4", "Hidden News", group = "News", hidden = true, number = 2),
            channel(2, "5", "Sky News", group = "Secret", number = 3),
        )
        val hiddenGroups = setOf(2L to "Secret")
        fun names(filters: SearchFilters) = ChannelSearch.search(channels, hiddenGroups, filters).map { it.name }

        assertEquals(listOf("Télé-Québec"), names(SearchFilters(query = "tele queb")))
        // Names starting with the word come first.
        assertEquals(listOf("News 24", "BBC News"), names(SearchFilters(query = "news")))
        assertEquals(listOf("BBC News"), names(SearchFilters(query = "news", country = "UK")))
        assertEquals(listOf("News 24"), names(SearchFilters(group = "News")))
        assertEquals(listOf("News 24"), names(SearchFilters(query = "news", playlistId = 2)))
        assertTrue(names(SearchFilters()).isEmpty())
        assertEquals(listOf("CA" to 1, "UK" to 1, "AL" to 1).toSet(), ChannelSearch.countries(channels.take(3)).toSet())
    }

    @Test
    fun `health from http answers`() {
        assertEquals(HealthStatus.Working, HealthProbe.classify(200, "application/vnd.apple.mpegurl", "#EXTM3U"))
        assertEquals(HealthStatus.Working, HealthProbe.classify(206, "video/mp2t", "G@\u0000"))
        assertEquals(HealthStatus.Blocked, HealthProbe.classify(403, null, null))
        assertEquals(HealthStatus.Offline, HealthProbe.classify(404, null, null))
        assertEquals(HealthStatus.Offline, HealthProbe.classify(200, "text/html; charset=utf-8", "<!DOCTYPE html>"))
        assertEquals(HealthStatus.Offline, HealthProbe.classify(200, null, ""))
    }
}
