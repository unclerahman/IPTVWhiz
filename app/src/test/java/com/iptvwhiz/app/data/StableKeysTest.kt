package com.iptvwhiz.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableKeysTest {

    @Test
    fun `same channel gets the same key across refreshes`() {
        val first = StableKeys().forM3u("bbc1.uk", "BBC One", "UK")
        val second = StableKeys().forM3u("bbc1.uk", "  bbc   one ", "Other group")
        assertEquals(first, second)
    }

    @Test
    fun `without tvg-id the group is part of the key`() {
        val keys = StableKeys()
        assertNotEquals(keys.forM3u(null, "News", "UK"), keys.forM3u(null, "News", "US"))
    }

    @Test
    fun `duplicates are numbered in order`() {
        val keys = StableKeys()
        assertEquals("n:uk|news", keys.forM3u(null, "News", "UK"))
        assertEquals("n:uk|news#2", keys.forM3u(null, "News", "UK"))
        assertEquals("n:uk|news#3", keys.forM3u("", "News", "UK"))
    }

    @Test
    fun `xtream keys use the stream id`() {
        assertEquals("x:42", StableKeys().forXtream("42"))
    }
}
