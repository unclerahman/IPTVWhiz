package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.db.ProgramSlot
import com.iptvwhiz.app.data.epg.GuideBlock
import com.iptvwhiz.app.data.epg.GuideTimeline
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.HealthBadge
import com.iptvwhiz.app.ui.common.formatTime
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.rememberNow
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.theme.Brand
import kotlinx.coroutines.launch

/** Half an hour is 120 dp: a little over an hour fits beside the channel column on a phone. */
private val MinuteWidth = 4.dp
private val ChannelCellWidth = 116.dp
private val GuideRowHeight = 68.dp
private const val GUIDE_HOURS = 12

/**
 * Guide section: a timeline, channels down the side and the next 12 hours across. Swipe sideways
 * to look ahead (every row moves together) and up and down for more channels. Tapping what's on
 * now plays the channel; a later programme opens its details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileGuideContent(vm: LiveViewModel, onPlay: (Channel, group: String) -> Unit, modifier: Modifier) {
    val organized by vm.organized.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val channels = remember(organized, guide) {
        organized?.browse(OrganizedPlaylist.ALL).orEmpty().filter { guide[it.ref] != null }
    }
    if (channels.isEmpty()) {
        MobileEmptyState(
            EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message),
            modifier,
            icon = Icons.Filled.DateRange,
        )
        return
    }

    val now by rememberNow()
    val from = GuideTimeline.windowStart(now)
    val until = from + GUIDE_HOURS * 3_600_000L
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<Pair<Channel, ProgramSlot>?>(null) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.guide_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                enabled = scroll.value > 0,
            ) { Text(stringResource(R.string.guide_back_to_now)) }
        }
        Row {
            Spacer(Modifier.width(ChannelCellWidth))
            TimeRuler(from, until, now, Modifier.weight(1f).horizontalScroll(scroll))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(state = listState) {
            itemsIndexed(channels, key = { _, it -> "${it.playlistId}|${it.stableKey}" }) { _, channel ->
                val blocks = remember(schedule[channel.ref], from, until) {
                    GuideTimeline.blocks(schedule[channel.ref].orEmpty(), from, until)
                }
                Row(Modifier.height(GuideRowHeight), verticalAlignment = Alignment.CenterVertically) {
                    GuideChannelCell(
                        channel,
                        health[channel.ref],
                        channel.ref in favorites,
                        onClick = { onPlay(channel, OrganizedPlaylist.ALL) },
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Row(Modifier.fillMaxHeight().padding(vertical = 3.dp)) {
                            blocks.forEach { block ->
                                GuideBlockTile(block, now, rememberStickyShift(block, from, scroll, 96.dp)) {
                                    val slot = block.slot
                                    if (block.isOn(now) || slot == null) onPlay(channel, OrganizedPlaylist.ALL) else details = channel to slot
                                }
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
        ModalBottomSheet(onDismissRequest = { details = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(slot.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${channel.number}  ${channel.name} · ${context.formatTimeRange(slot)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                slot.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        details = null
                        onPlay(channel, OrganizedPlaylist.ALL)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.guide_watch_channel)) }
            }
        }
    }
}

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
    Box(modifier.height(28.dp)) {
        Box(Modifier.width(MinuteWidth * ((until - from) / 60_000f)).fillMaxHeight())
        GuideTimeline.ticks(from, until).forEach { tick ->
            Text(
                context.formatTime(tick),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = MinuteWidth * ((tick - from) / 60_000f) + 6.dp),
            )
        }
        CurrentTimeLine(from, now)
    }
}

/** The thin red line at the current time. */
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

/** Logo, number and name with the same status and favourite marks as Live TV. Tap to watch. */
@Composable
private fun GuideChannelCell(channel: Channel, health: HealthStatus?, isFavorite: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .width(ChannelCellWidth)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.name, channel.logoUrl, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.label_favorite),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    )
                }
            }
            Text(channel.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HealthBadge(health)
        }
    }
}

/** One programme (or gap). What's on now is lit, with the part already shown shaded. */
@Composable
private fun GuideBlockTile(block: GuideBlock, now: Long, stickyShift: () -> Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val onAir = block.isOn(now)
    val past = block.stop <= now
    val width = MinuteWidth * block.minutes
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    onAir -> MaterialTheme.colorScheme.secondaryContainer
                    block.slot == null || past -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick),
    ) {
        if (onAir) {
            val shown = ((now - block.start).toFloat() / (block.stop - block.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Color.White.copy(alpha = 0.06f)))
        }
        if (width >= 36.dp) {
            Column(
                Modifier.offset { IntOffset(stickyShift(), 0) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    block.slot?.title ?: stringResource(R.string.guide_nothing_now),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (onAir) FontWeight.SemiBold else FontWeight.Medium),
                    color = when {
                        onAir -> MaterialTheme.colorScheme.onSecondaryContainer
                        past || block.slot == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                block.slot?.let {
                    Text(
                        context.formatTimeRange(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
