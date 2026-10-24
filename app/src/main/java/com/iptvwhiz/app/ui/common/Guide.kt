package com.iptvwhiz.app.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.EpgStatus
import com.iptvwhiz.app.data.db.ProgramSlot
import com.iptvwhiz.app.data.epg.EpgRepository
import com.iptvwhiz.app.data.epg.EpgWork
import java.util.Date

/** Thin bar showing how far into the current programme we are. Theme-neutral: colours passed in. */
@Composable
fun ProgramProgress(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

/** "13:45" or "1:45 PM", following the device setting. */
fun Context.formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

/** "12:00–13:00" */
fun Context.formatTimeRange(slot: ProgramSlot): String = "${formatTime(slot.start)}–${formatTime(slot.stop)}"

/** "Next 13:00 · Title" */
fun Context.nextLine(slot: ProgramSlot): String = getString(R.string.guide_next, formatTime(slot.start), slot.title)

/**
 * Status line for the TV guide settings. [linkCount] is how many guide links the playlist has;
 * only [EpgRepository.MAX_LINKS] are used per update.
 */
fun Context.epgStatusMessage(status: EpgStatus?, work: EpgWork, linkCount: Int): String = when {
    work is EpgWork.Running -> if (work.total > 1) {
        getString(R.string.guide_updating_sources, work.done + 1, work.total)
    } else {
        getString(R.string.guide_updating)
    }
    linkCount == 0 -> getString(R.string.guide_no_link)
    status == null -> getString(R.string.guide_not_loaded)
    status.lastError == EpgRepository.ERROR_NO_LINK -> getString(R.string.guide_no_link)
    else -> buildList {
        if (status.lastUpdated > 0) {
            add(
                getString(
                    R.string.guide_updated,
                    DateUtils.getRelativeTimeSpanString(status.lastUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                ),
            )
            add(resources.getQuantityString(R.plurals.guide_channels_matched, status.matchedChannels, formatCount(status.matchedChannels)))
            add(resources.getQuantityString(R.plurals.guide_programmes, status.programmes, formatCount(status.programmes)))
        }
        when {
            status.lastError == null -> Unit
            status.lastError.startsWith(EpgRepository.PARTIAL_PREFIX) -> add(getString(R.string.guide_some_failed))
            else -> add(getString(R.string.guide_last_failed))
        }
        if (linkCount > EpgRepository.MAX_LINKS) add(getString(R.string.guide_links_capped, EpgRepository.MAX_LINKS, linkCount))
    }.joinToString(" · ").ifEmpty { getString(R.string.guide_not_loaded) }
}
