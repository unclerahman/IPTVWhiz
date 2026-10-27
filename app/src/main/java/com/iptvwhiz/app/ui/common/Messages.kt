package com.iptvwhiz.app.ui.common

import android.content.Context
import android.text.format.DateUtils
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ImportError
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.data.db.PlaylistType
import com.iptvwhiz.app.player.PlaybackProblem
import java.text.NumberFormat

/** Plain-language explanation of an import failure (PLAN.md §8: no raw errors on the main screen). */
fun Context.importErrorMessage(error: ImportError): String = when (error) {
    ImportError.NoConnection -> getString(R.string.import_error_no_connection)
    ImportError.Timeout -> getString(R.string.import_error_timeout)
    ImportError.SecureConnectionFailed -> getString(R.string.import_error_ssl)
    ImportError.AccessDenied -> getString(R.string.import_error_access_denied)
    ImportError.NotFound -> getString(R.string.import_error_not_found)
    is ImportError.HttpError -> getString(R.string.import_error_http, error.code)
    ImportError.NotAPlaylist -> getString(R.string.import_error_not_playlist)
    ImportError.Empty -> getString(R.string.import_error_empty)
    ImportError.WrongLogin -> getString(R.string.import_error_wrong_login)
    is ImportError.AccountInactive -> getString(R.string.import_error_account_inactive, error.status)
    ImportError.FileUnreadable -> getString(R.string.import_error_file)
    ImportError.InvalidAddress -> getString(R.string.import_error_invalid_address)
    is ImportError.Unknown -> getString(R.string.import_error_unknown)
}

fun Context.playbackProblemMessage(problem: PlaybackProblem): String = getString(
    when (problem) {
        PlaybackProblem.Offline -> R.string.playback_offline
        PlaybackProblem.Blocked -> R.string.playback_blocked
        PlaybackProblem.NoConnection -> R.string.playback_no_connection
        PlaybackProblem.Unsupported -> R.string.playback_unsupported
        PlaybackProblem.Drm -> R.string.playback_drm
        PlaybackProblem.NoVideo -> R.string.playback_no_video
        PlaybackProblem.Other -> R.string.playback_other
    },
)

fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

/** "Xtream · 2,052 channels · Updated 5 minutes ago" */
fun Context.playlistSummary(playlist: PlaylistEntity): String {
    val type = getString(
        when (playlist.type) {
            PlaylistType.M3U_URL -> R.string.playlist_type_m3u
            PlaylistType.M3U_FILE -> R.string.playlist_type_file
            PlaylistType.XTREAM -> R.string.playlist_type_xtream
        },
    )
    val channels = resources.getQuantityString(
        R.plurals.channel_count, playlist.channelCount, formatCount(playlist.channelCount),
    )
    val now = System.currentTimeMillis()
    val updated = if (now - playlist.lastRefreshed < DateUtils.MINUTE_IN_MILLIS) {
        getString(R.string.just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(playlist.lastRefreshed, now, DateUtils.MINUTE_IN_MILLIS)
    }
    return getString(R.string.playlist_summary, type, channels, updated)
}
