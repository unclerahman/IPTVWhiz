package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.db.ProgramSlot
import com.iptvwhiz.app.data.epg.EpgRepository
import com.iptvwhiz.app.data.epg.EpgWork
import com.iptvwhiz.app.data.epg.GuideBlock
import com.iptvwhiz.app.data.epg.GuideTimeline
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.HealthBadge
import com.iptvwhiz.app.ui.common.epgStatusMessage
import com.iptvwhiz.app.ui.common.formatTime
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.rememberNow
import com.iptvwhiz.app.ui.common.requestFocusUntil
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.EpgSettingsViewModel
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.Brand
import com.iptvwhiz.app.ui.theme.TvSafeArea
import kotlinx.coroutines.launch

/** Width of one minute in the timeline: half an hour is 180 dp, so about two hours fit on screen. */
private val MinuteWidth = 6.dp
private val ChannelCellWidth = 230.dp
private val GuideRowHeight = 76.dp

/**
 * Guide section: a timeline, channels down the side and the next 12 hours across. Every row
 * scrolls sideways together; ◀ ▶ move between programmes, ▲ ▼ between channels at about the same
 * time. OK on a programme that's on now plays the channel; a later one shows its details.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvGuideContent(onPlay: (Channel, group: String) -> Unit) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val organized by vm.organized.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val channels = remember(organized, guide) {
        organized?.browse(OrganizedPlaylist.ALL).orEmpty().filter { guide[it.ref] != null }
    }

    if (channels.isEmpty()) {
        TvEmptyState(EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message))
        return
    }

    val now by rememberNow()
    val from = GuideTimeline.windowStart(now)
    val until = from + GUIDE_HOURS * 3_600_000L
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val nowFocus = remember { FocusRequester() }
    var details by remember { mutableStateOf<Pair<Channel, ProgramSlot>?>(null) }
    val listState = rememberLazyListState()

    var gridHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { nowFocus.requestFocusUntil({ gridHasFocus }) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_now_next_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(24.dp))
            GuideChip(
                label = stringResource(R.string.guide_back_to_now),
                onClick = {
                    scope.launch {
                        scroll.animateScrollTo(0)
                        listState.scrollToItem(0)
                        nowFocus.requestFocusWhenReady()
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Spacer(Modifier.width(ChannelCellWidth))
            TimeRuler(from, until, now, Modifier.weight(1f).horizontalScroll(scroll))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .focusRestorer { nowFocus }
                .onFocusChanged { gridHasFocus = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(channels, key = { _, it -> "${it.playlistId}|${it.stableKey}" }) { index, channel ->
                val blocks = remember(schedule[channel.ref], from, until) {
                    GuideTimeline.blocks(schedule[channel.ref].orEmpty(), from, until)
                }
                Row(Modifier.height(GuideRowHeight), verticalAlignment = Alignment.CenterVertically) {
                    GuideChannelCell(channel, health[channel.ref], channel.ref in favorites)
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Row(Modifier.fillMaxHeight()) {
                            blocks.forEach { block ->
                                val onAir = block.isOn(now)
                                GuideBlockTile(
                                    block = block,
                                    now = now,
                                    stickyShift = rememberStickyShift(block, from, scroll, 140.dp),
                                    onClick = {
                                        val slot = block.slot
                                        if (onAir || slot == null) onPlay(channel, OrganizedPlaylist.ALL) else details = channel to slot
                                    },
                                    modifier = if (index == 0 && onAir) Modifier.focusRequester(nowFocus) else Modifier,
                                )
                            }
                        }
                        CurrentTimeLine(from, now)
                    }
                }
            }
        }
    }

    details?.let { (channel, slot) ->
        val context = LocalContext.current
        TvDialog(
            title = slot.title,
            message = "${channel.number}  ${channel.name} · ${context.formatTimeRange(slot)}",
            onDismiss = { details = null },
        ) {
            slot.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            val watch = remember { FocusRequester() }
            LaunchedEffect(Unit) { watch.requestFocusWhenReady() }
            Button(
                onClick = {
                    details = null
                    onPlay(channel, OrganizedPlaylist.ALL)
                },
                modifier = Modifier.focusRequester(watch),
            ) { Text(stringResource(R.string.guide_watch_channel)) }
        }
    }
}

private const val GUIDE_HOURS = 12

/**
 * How far to push a block's title right so it stays readable while the start of a long programme
 * is scrolled off the left edge. Read in the layout phase, so scrolling doesn't recompose the rows.
 */
@Composable
private fun rememberStickyShift(block: GuideBlock, from: Long, scroll: ScrollState, minVisible: Dp): () -> Int {
    val density = LocalDensity.current
    val start = with(density) { (MinuteWidth * ((block.start - from) / 60_000f)).toPx() }
    val room = with(density) { (MinuteWidth * block.minutes - minVisible).toPx() }.coerceAtLeast(0f)
    return { (scroll.value - start).coerceIn(0f, room).toInt() }
}

/** Half-hour marks over the timeline. */
@Composable
private fun TimeRuler(from: Long, until: Long, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.height(32.dp)) {
        Box(Modifier.width(MinuteWidth * ((until - from) / 60_000f)).fillMaxHeight())
        GuideTimeline.ticks(from, until).forEach { tick ->
            Text(
                context.formatTime(tick),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = MinuteWidth * ((tick - from) / 60_000f) + 8.dp),
            )
        }
        CurrentTimeLine(from, now)
    }
}

/** The thin red line at the current time, drawn over each row. */
@Composable
private fun CurrentTimeLine(from: Long, now: Long) {
    Box(
        Modifier
            .offset(x = MinuteWidth * ((now - from) / 60_000f))
            .width(2.dp)
            .fillMaxHeight()
            .background(Brand.Error),
    )
}

/** Number, logo, name, and the same status and favourite marks as Live TV. Not focusable: the programmes are. */
@Composable
private fun GuideChannelCell(channel: Channel, health: HealthStatus?, isFavorite: Boolean) {
    Row(
        Modifier.width(ChannelCellWidth).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            channel.number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        ChannelLogo(channel.name, channel.logoUrl, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthBadge(health, textStyle = MaterialTheme.typography.labelSmall)
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.label_favorite),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
        }
    }
}

/** One programme (or gap) in the timeline. On-air programmes are lit; the part already shown is shaded. */
@Composable
private fun GuideBlockTile(
    block: GuideBlock,
    now: Long,
    stickyShift: () -> Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onAir = block.isOn(now)
    val past = block.stop <= now
    val width = MinuteWidth * block.minutes
    Surface(
        onClick = onClick,
        modifier = modifier.width(width).fillMaxHeight().padding(end = 3.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                onAir -> Brand.IndigoDeep
                block.slot == null || past -> Brand.DarkSurfaceContainerLow
                else -> Brand.DarkSurface
            },
            contentColor = if (past) Brand.DarkOnSurfaceVariant else Brand.DarkOnSurface,
            focusedContainerColor = Brand.FocusLight,
            focusedContentColor = Brand.IndigoDeep,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        if (onAir) {
            // Shade the part of the programme that has already been shown.
            val shown = ((now - block.start).toFloat() / (block.stop - block.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Color.White.copy(alpha = 0.06f)))
        }
        if (width >= 48.dp) {
            Column(Modifier.offset { IntOffset(stickyShift(), 0) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    block.slot?.title ?: stringResource(R.string.guide_nothing_now),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                block.slot?.let {
                    Text(
                        context.formatTimeRange(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A small focusable pill for the guide header. */
@Composable
private fun GuideChip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surfaceVariant,
            contentColor = colors.onSurfaceVariant,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
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
