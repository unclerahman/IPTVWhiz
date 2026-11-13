package com.iptvwhiz.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNamesTest {

    @Test
    fun `addresses are not names, server names are`() {
        assertNull(PlaylistNames.fromHost("10.0.2.2"))
        assertNull(PlaylistNames.fromHost("192.168.1.20:8080"))
        assertNull(PlaylistNames.fromHost("localhost"))
        assertNull(PlaylistNames.fromHost("[::1]"))
        assertNull(PlaylistNames.fromHost(""))
        assertNull(PlaylistNames.fromHost(null))
        assertEquals("provider.example", PlaylistNames.fromHost("provider.example"))
        assertEquals("iptv-org/iptv", PlaylistNames.fromHost("iptv-org/iptv"))
    }

    @Test
    fun `names are numbered when taken, ignoring case`() {
        assertEquals("My playlist", PlaylistNames.unique("My playlist", emptyList()))
        assertEquals("My playlist 2", PlaylistNames.unique("My playlist", listOf("my playlist")))
        assertEquals("My playlist 3", PlaylistNames.unique("My playlist", listOf("My playlist", "My playlist 2")))
        assertEquals("Sports", PlaylistNames.unique("Sports", listOf("My playlist")))
    }
}
