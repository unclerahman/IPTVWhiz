package com.iptvwhiz.app.ui.common

import androidx.annotation.StringRes
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.Routes
import com.iptvwhiz.app.ui.TopLevel

/** Text for a section's empty state; shared by the TV and mobile layouts. */
data class EmptyStateText(@StringRes val title: Int, @StringRes val message: Int)

fun TopLevel.emptyState(): EmptyStateText? = when (this) {
    TopLevel.Live -> null
    TopLevel.Guide -> EmptyStateText(R.string.empty_guide_title, R.string.empty_guide_message)
    TopLevel.Favorites -> EmptyStateText(R.string.empty_favorites_title, R.string.empty_favorites_message)
    TopLevel.Search -> EmptyStateText(R.string.empty_search_title, R.string.empty_search_message)
    TopLevel.Settings -> null
}

val NoPlaylistText = EmptyStateText(R.string.empty_live_title, R.string.empty_live_message)

/** [route] null = not built yet ("coming soon"). */
data class SettingsEntry(@StringRes val title: Int, @StringRes val description: Int, val route: String? = null)

val settingsEntries = listOf(
    SettingsEntry(R.string.settings_playlists, R.string.settings_playlists_desc, Routes.PLAYLISTS),
    SettingsEntry(R.string.settings_epg, R.string.settings_epg_desc),
    SettingsEntry(R.string.settings_manage, R.string.settings_manage_desc),
    SettingsEntry(R.string.settings_player, R.string.settings_player_desc),
    SettingsEntry(R.string.settings_appearance, R.string.settings_appearance_desc),
    SettingsEntry(R.string.settings_backup, R.string.settings_backup_desc),
    SettingsEntry(R.string.settings_about, R.string.settings_about_desc),
)

/** Accepts what people actually paste: missing scheme is not accepted, but surrounding spaces are. */
fun isValidUrl(text: String): Boolean {
    val trimmed = text.trim()
    return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
        trimmed.length > "https://".length
}
