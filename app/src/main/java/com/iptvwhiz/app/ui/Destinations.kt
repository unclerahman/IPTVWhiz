package com.iptvwhiz.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val ADD_M3U = "add/m3u"
    const val ADD_XTREAM = "add/xtream"
    const val PLAYLISTS = "playlists"
    const val BACKUP = "backup"
    const val EPG = "epg"
    const val VIEWING = "viewing"
    const val UPDATE = "update"

    const val ARG_PLAYLIST = "playlistId"
    const val ARG_GROUP = "group"
    const val ARG_KEY = "key"
    const val ARG_SOURCE = "src"
    const val SOURCE_GROUP = "group"
    const val SOURCE_WATCHLIST = "watchlist"
    const val SOURCE_QUEUE = "queue"
    const val PLAYER = "player?$ARG_SOURCE={$ARG_SOURCE}&$ARG_PLAYLIST={$ARG_PLAYLIST}&$ARG_GROUP={$ARG_GROUP}&$ARG_KEY={$ARG_KEY}"

    /**
     * Plays [channel] with zapping inside [browseKey]: a group of its playlist, "All channels",
     * or a watchlist key (then zapping can cross playlists).
     */
    fun player(channel: Channel, browseKey: String): String {
        val source = if (OrganizedPlaylist.watchlistId(browseKey) != null) SOURCE_WATCHLIST else SOURCE_GROUP
        return "player?$ARG_SOURCE=$source&$ARG_PLAYLIST=${channel.playlistId}" +
            "&$ARG_GROUP=${Uri.encode(browseKey)}&$ARG_KEY=${Uri.encode(channel.stableKey)}"
    }

    /** Plays [channel] with zapping through AppContainer.playQueue (e.g. search results). */
    fun playerQueue(channel: Channel): String =
        "player?$ARG_SOURCE=$SOURCE_QUEUE&$ARG_PLAYLIST=${channel.playlistId}&$ARG_KEY=${Uri.encode(channel.stableKey)}"

    const val WATCHLISTS = "watchlists"
    const val WATCHLIST = "watchlists/list?$ARG_GROUP={$ARG_GROUP}"

    fun watchlist(id: Long): String = "watchlists/list?$ARG_GROUP=$id"

    const val MANAGE = "manage"
    const val MANAGE_GROUP = "manage/group?$ARG_GROUP={$ARG_GROUP}"

    fun manageGroup(groupKey: String): String = "manage/group?$ARG_GROUP=${Uri.encode(groupKey)}"

    val manageGroupArguments = listOf(
        navArgument(ARG_GROUP) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

    val playerArguments = listOf(
        navArgument(ARG_SOURCE) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument(ARG_PLAYLIST) {
            type = NavType.LongType
            defaultValue = -1L
        },
        navArgument(ARG_GROUP) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument(ARG_KEY) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )
}

enum class TopLevel(@StringRes val label: Int, val icon: ImageVector) {
    Live(R.string.nav_live, Icons.Filled.PlayArrow),
    Guide(R.string.nav_guide, Icons.Filled.DateRange),
    Favorites(R.string.nav_watchlists, Icons.Filled.Favorite),
    Search(R.string.nav_search, Icons.Filled.Search),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}
