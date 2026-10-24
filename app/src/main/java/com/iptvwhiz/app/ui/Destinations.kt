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

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val ADD_M3U = "add/m3u"
    const val ADD_XTREAM = "add/xtream"
    const val PLAYLISTS = "playlists"
    const val BACKUP = "backup"

    const val ARG_PLAYLIST = "playlistId"
    const val ARG_GROUP = "group"
    const val ARG_KEY = "key"
    const val PLAYER = "player/{$ARG_PLAYLIST}?$ARG_GROUP={$ARG_GROUP}&$ARG_KEY={$ARG_KEY}"

    /** [group] is a group key or OrganizedPlaylist.ALL / FAVORITES; zapping stays inside it. */
    fun player(playlistId: Long, group: String, stableKey: String): String =
        "player/$playlistId?$ARG_KEY=${Uri.encode(stableKey)}&$ARG_GROUP=${Uri.encode(group)}"

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
        navArgument(ARG_PLAYLIST) { type = NavType.LongType },
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
    Favorites(R.string.nav_favorites, Icons.Filled.Favorite),
    Search(R.string.nav_search, Icons.Filled.Search),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}
