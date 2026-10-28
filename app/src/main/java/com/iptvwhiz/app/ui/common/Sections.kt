package com.iptvwhiz.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.Routes
import com.iptvwhiz.app.ui.TopLevel

/** Text for a section's empty state; shared by the TV and mobile layouts. */
data class EmptyStateText(@StringRes val title: Int, @StringRes val message: Int)

fun TopLevel.emptyState(): EmptyStateText? = when (this) {
    TopLevel.Live -> null
    TopLevel.Guide -> null
    TopLevel.Favorites -> null
    TopLevel.Search -> EmptyStateText(R.string.empty_search_title, R.string.empty_search_message)
    TopLevel.Settings -> null
}

val NoPlaylistText = EmptyStateText(R.string.empty_live_title, R.string.empty_live_message)

/** A group of related settings, rendered with a small header above it. */
enum class SettingsSection(@StringRes val title: Int) {
    Setup(R.string.settings_section_setup),
    Playback(R.string.settings_section_playback),
    AppData(R.string.settings_section_app_data),
}

/** [route] null = not built yet ("coming soon"). */
data class SettingsEntry(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val route: String? = null,
    val icon: ImageVector = Icons.Filled.Settings,
    val section: SettingsSection = SettingsSection.Setup,
)

val settingsEntries = listOf(
    SettingsEntry(
        R.string.settings_playlists,
        R.string.settings_playlists_desc,
        route = Routes.PLAYLISTS,
        icon = Icons.AutoMirrored.Filled.List,
    ),
    SettingsEntry(
        R.string.settings_epg,
        R.string.settings_epg_desc,
        route = Routes.EPG,
        icon = Icons.Filled.DateRange,
    ),
    SettingsEntry(
        R.string.settings_manage,
        R.string.settings_manage_desc,
        route = Routes.MANAGE,
        icon = Icons.Filled.Edit,
    ),
    SettingsEntry(
        R.string.settings_watchlists,
        R.string.settings_watchlists_desc,
        route = Routes.WATCHLISTS,
        icon = Icons.Filled.Favorite,
    ),
    SettingsEntry(
        R.string.settings_viewing,
        R.string.settings_viewing_desc,
        route = Routes.VIEWING,
        icon = Icons.Filled.CheckCircle,
        section = SettingsSection.Playback,
    ),
    SettingsEntry(
        R.string.settings_player,
        R.string.settings_player_desc,
        icon = Icons.Filled.PlayArrow,
        section = SettingsSection.Playback,
    ),
    SettingsEntry(
        R.string.settings_appearance,
        R.string.settings_appearance_desc,
        icon = Icons.Filled.Star,
        section = SettingsSection.Playback,
    ),
    SettingsEntry(
        R.string.settings_backup,
        R.string.settings_backup_desc,
        route = Routes.BACKUP,
        icon = Icons.Filled.Share,
        section = SettingsSection.AppData,
    ),
    SettingsEntry(
        R.string.settings_about,
        R.string.settings_about_desc,
        icon = Icons.Filled.Info,
        section = SettingsSection.AppData,
    ),
)

/** Accepts what people actually paste: missing scheme is not accepted, but surrounding spaces are. */
fun isValidUrl(text: String): Boolean {
    val trimmed = text.trim()
    return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
        trimmed.length > "https://".length
}

val NoWatchlistsText = EmptyStateText(R.string.empty_favorites_title, R.string.empty_favorites_message)

/** Label for a browse key: "All channels", Favorites, or a group's or watchlist's name. */
@Composable
fun groupLabel(key: String, displayName: String?): String = when {
    key == OrganizedPlaylist.ALL -> stringResource(R.string.all_channels)
    displayName == null && OrganizedPlaylist.watchlistId(key) != null -> stringResource(R.string.watchlist_favorites)
    else -> displayName ?: key
}

/** A watchlist's name; the built-in Favorites list follows the device language. */
@Composable
fun watchlistName(list: Watchlist): String = if (list.isFavorites) stringResource(R.string.watchlist_favorites) else list.name
