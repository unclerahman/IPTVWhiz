package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.data.db.ProgramSlot
import com.iptvwhiz.app.data.epg.EpgRepository
import com.iptvwhiz.app.data.epg.EpgWork
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.data.epg.slotAt
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.epgStatusMessage
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.formatTime
import com.iptvwhiz.app.ui.common.nextLine
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.EpgSettingsViewModel
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea

/** Guide section: every channel with a guide, what's on now and what's next — or at a picked hour. */
@Composable
fun TvGuideContent(onPlay: (Channel, group: String) -> Unit) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val context = LocalContext.current
    val organized by vm.organized.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val channels = remember(organized, guide) {
        organized?.browse(OrganizedPlaylist.ALL).orEmpty().filter { guide[it.ref] != null }
    }

    if (channels.isEmpty()) {
        TvEmptyState(EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message))
        return
    }

    // 0 = now; 1..12 = that many hours ahead.
    var hourOffset by rememberSaveable { mutableIntStateOf(0) }
    val now = remember(hourOffset) { System.currentTimeMillis() } // recompute point-in-time when the user jumps

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_now_next_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(24.dp))
            TimeJumpRow(
                selected = hourOffset,
                onSelect = { hourOffset = it },
            )
        }
        Spacer(Modifier.size(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels, key = { it.stableKey }) { channel ->
                GuideChannelRow(channel, guide[channel.ref], schedule[channel.ref], hourOffset, onPlay)
            }
        }
    }
}

/** Now · +1 h · … · +12 h: how far ahead the guide looks. */
@Composable
private fun TimeJumpRow(selected: Int, onSelect: (Int) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenReady() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        (0..12).forEach { hour ->
            val label = if (hour == 0) stringResource(R.string.guide_now)
                else stringResource(R.string.guide_hours_ahead, hour)
            TvTimeChip(
                label = label,
                selected = selected == hour,
                onClick = { onSelect(hour) },
                modifier = if (hour == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

/** One focusable time chip; TV rows are ListItems, but a compact chip suits the time strip. */
@Composable
private fun TvTimeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) colors.primary else colors.surfaceVariant,
            contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

private val ChipShape = RoundedCornerShape(20.dp)

/** One channel row: now/next at hour 0, the programme at that hour otherwise. */
@Composable
private fun GuideChannelRow(
    channel: Channel,
    programme: NowNext?,
    slots: List<ProgramSlot>?,
    hourOffset: Int,
    onPlay: (Channel, String) -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        selected = false,
        onClick = { onPlay(channel, OrganizedPlaylist.ALL) },
        scale = ListItemDefaults.scale(focusedScale = 1.01f),
        leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 48.dp) },
        overlineContent = {
            Text("${channel.number}  ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        headlineContent = {
            if (hourOffset == 0) {
                Text(
                    programme?.now?.let { "${context.formatTimeRange(it)}   ${it.title}" }
                        ?: stringResource(R.string.guide_nothing_now),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val at = System.currentTimeMillis() + hourOffset * 3_600_000L
                val slot = slots?.slotAt(at)
                Text(
                    slot?.let { "${context.formatTimeRange(it)}   ${it.title}" }
                        ?: stringResource(R.string.guide_nothing_now),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hourOffset == 0) {
                    ProgramProgress(
                        fraction = programme?.progress(System.currentTimeMillis()) ?: 0f,
                        color = LocalContentColor.current,
                        track = LocalContentColor.current.copy(alpha = 0.25f),
                        modifier = Modifier.width(240.dp),
                    )
                    Spacer(Modifier.width(24.dp))
                    Text(
                        programme?.next?.let { context.nextLine(it) }.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // After the picked hour: the next thing after the slot covering it.
                    val at = System.currentTimeMillis() + hourOffset * 3_600_000L
                    val after = slots?.firstOrNull { it.start >= at }?.takeIf { it != slots.slotAt(at) }
                    Text(
                        after?.let { context.nextLine(it) }
                            ?: stringResource(R.string.guide_nothing_now),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

/** Settings → TV guide: status, guide links and "update now". */
@Composable
fun TvEpgSettingsScreen() {
    val vm = appViewModel { container, _ -> EpgSettingsViewModel(container) }
    val context = LocalContext.current
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val work by vm.work.collectAsStateWithLifecycle()
    var links by remember(playlist?.id) { mutableStateOf(playlist?.epgUrl.orEmpty().replace(",", ", ")) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenReady() }

    val current = playlist
    if (current == null) {
        TvEmptyState(EmptyStateText(R.string.guide_empty_title, R.string.manage_empty))
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_epg), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.guide_settings_intro),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${current.name}: " + context.epgStatusMessage(status, work, EpgRepository.links(current.epgUrl).size),
            style = MaterialTheme.typography.titleMedium,
            color = if (work is EpgWork.Running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        )
        Button(onClick = vm::updateNow, enabled = work !is EpgWork.Running, modifier = Modifier.focusRequester(firstFocus)) {
            Text(stringResource(R.string.guide_update_now))
        }
        TvTextField(
            label = stringResource(R.string.guide_links),
            value = links,
            onValueChange = { links = it },
            placeholder = stringResource(R.string.guide_links_hint),
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        OutlinedButton(onClick = { vm.saveLinks(links) }) { Text(stringResource(R.string.guide_save_link)) }
    }
}
