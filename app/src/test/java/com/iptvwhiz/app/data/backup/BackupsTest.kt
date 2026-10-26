package com.iptvwhiz.app.data.backup

import com.iptvwhiz.app.data.db.ChannelOverride
import com.iptvwhiz.app.data.db.GroupSetting
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.PlaylistType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupsTest {

    private val xtream = PlaylistEntity(
        id = 5, name = "My provider", type = PlaylistType.XTREAM, source = "http://host.tv:8080/",
        username = "user", password = "secret",
    )

    @Test
    fun `fingerprint ignores database id and password, but not the account`() {
        val sameOtherDevice = xtream.copy(id = 99, source = "host.tv:8080", password = "changed")
        assertEquals(Backups.fingerprint(xtream), Backups.fingerprint(sameOtherDevice))
        assertNotEquals(Backups.fingerprint(xtream), Backups.fingerprint(xtream.copy(username = "someone-else")))
        assertFalse(Backups.fingerprint(xtream).contains("user"))
    }

    @Test
    fun `logins are left out unless asked for`() {
        val without = Backups.toBackup(xtream, emptyList(), emptyList(), includeLogins = false)
        assertNull(without.source)
        assertNull(without.username)
        assertNull(without.password)
        assertNull(without.epgUrl)
        val with = Backups.toBackup(xtream, emptyList(), emptyList(), includeLogins = true)
        assertEquals("secret", with.password)
    }

    @Test
    fun `newest edit wins per channel`() {
        val local = listOf(
            ChannelOverride(1, "a", customName = "Local A", updatedAt = 200),
            ChannelOverride(1, "b", hidden = true, updatedAt = 100),
        )
        val incoming = listOf(
            BackupChannel("a", customName = "Remote A", updatedAt = 150), // older: ignored
            BackupChannel("b", hidden = false, favorite = true, updatedAt = 300), // newer: applied
            BackupChannel("c", favorite = true, updatedAt = 50), // new here: applied
        )
        val applied = Backups.channelsToApply(1, local, incoming)
        assertEquals(listOf("b", "c"), applied.map { it.stableKey })
        assertFalse(applied[0].hidden)
        // Favorites moved to the Favorites watchlist; the old flag is only read for that.
        assertFalse(applied[0].favorite)
        assertEquals(listOf("b", "c"), Backups.legacyFavorites(BackupPlaylist("f", "n", PlaylistType.M3U_URL, channels = incoming)))
    }

    @Test
    fun `newest edit wins per group`() {
        val local = listOf(GroupSetting(1, "News", customName = "Mine", updatedAt = 500))
        val incoming = listOf(
            BackupGroup("News", customName = "Theirs", updatedAt = 400),
            BackupGroup("Kids", isCustom = true, updatedAt = 10),
        )
        assertEquals(listOf("Kids"), Backups.groupsToApply(1, local, incoming).map { it.name })
    }

    @Test
    fun `file round-trips through JSON`() {
        val json = Json { ignoreUnknownKeys = true }
        val file = BackupFile(
            exportedAt = 1, appVersion = "test",
            playlists = listOf(
                Backups.toBackup(
                    xtream,
                    listOf(ChannelOverride(5, "x:1", favorite = true, sortOrder = 1.5, updatedAt = 7)),
                    listOf(GroupSetting(5, "Sport", hidden = true, updatedAt = 8)),
                    includeLogins = false,
                ),
            ),
        )
        val text = json.encodeToString(BackupFile.serializer(), file)
        assertEquals(file, json.decodeFromString(BackupFile.serializer(), text))
        assertFalse(text.contains("secret"))
    }
}
