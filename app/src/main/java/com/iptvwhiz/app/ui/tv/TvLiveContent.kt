package com.iptvwhiz.app.ui.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.HealthDot
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.NoWatchlistsText
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.common.watchlistName
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/**
 * TiviMate-style browser: lists on the left, their channels on the right. Moving over a list shows
 * its channels straight away; OK plays, long-press OK or Menu edits.
 * Live TV shows watchlists, All channels and the playlist's groups; [watchlistsOnly] shows just watchlists.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLiveContent(
    onPlay: (Channel, browseKey: String) -> Unit,
    onAddPlaylist: () -> Unit,
    watchlistsOnly: Boolean = false,
) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val organized by vm.organized.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    if (playlists == null) return
    val active = playlist
    if (active == null) {
        TvEmptyState(NoPlaylistText, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }
    val shownLists = if (watchlistsOnly) watchlists else watchlists.filter { it.entries.isNotEmpty() }
    if (watchlistsOnly && watchlists.all { it.entries.isEmpty() }) {
        TvEmptyState(NoWatchlistsText)
        return
    }
    // The Watchlists section always shows a watchlist, never a group.
    LaunchedEffect(watchlistsOnly, shownLists.firstOrNull()?.id) {
        if (watchlistsOnly && OrganizedPlaylist.watchlistId(selectedGroup) == null) {
            shownLists.firstOrNull()?.let { vm.selectGroup(OrganizedPlaylist.watchlistKey(it.id)) }
        }
    }

    var lastPlayedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    val firstGroupFocus = remember { FocusRequester() }
    val playlistNames = playlists.orEmpty().associate { it.id to it.name }
    val inWatchlist = OrganizedPlaylist.watchlistId(selectedGroup) != null

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text(
                text = if (watchlistsOnly) stringResource(R.string.nav_watchlists) else active.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.focusRestorer { firstGroupFocus },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(shownLists, key = { _, it -> OrganizedPlaylist.watchlistKey(it.id) }) { index, list ->
                    val key = OrganizedPlaylist.watchlistKey(list.id)
                    GroupRow(
                        name = watchlistName(list),
                        count = list.available.size,
                        selected = selectedGroup == key,
                        onFocused = { vm.selectGroup(key) },
                        modifier = if (watchlistsOnly && index == 0) Modifier.focusRequester(firstGroupFocus) else Modifier,
                    )
                }
                if (!watchlistsOnly) {
                    item(key = OrganizedPlaylist.ALL) {
                        GroupRow(
                            name = stringResource(R.string.all_channels),
                            count = organized?.visibleCount ?: 0,
                            selected = selectedGroup == OrganizedPlaylist.ALL,
                            onFocused = { vm.selectGroup(OrganizedPlaylist.ALL) },
                            modifier = Modifier.focusRequester(firstGroupFocus),
                        )
                    }
                    items(groups, key = { it.key }) { group ->
                        GroupRow(
                            name = group.displayName,
                            count = group.visibleCount,
                            selected = selectedGroup == group.key,
                            onFocused = { vm.selectGroup(group.key) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            TvChannelList(
                channels = channels,
                guide = guide,
                health = health,
                favorites = favorites,
                subtitle = { channel ->
                    when {
                        inWatchlist && playlistNames.size > 1 -> "${playlistNames[channel.playlistId].orEmpty()} · ${channel.groupTitle}"
                        inWatchlist || selectedGroup == OrganizedPlaylist.ALL -> channel.groupTitle
                        else -> null
                    }
                },
                listKey = selectedGroup,
                lastPlayedKey = lastPlayedKey,
                onPlay = { channel ->
                    lastPlayedKey = channel.stableKey
                    onPlay(channel, selectedGroup)
                },
                onEdit = { editing = it },
            )
        }
    }

    editing?.let { channel ->
        val watchlistId = OrganizedPlaylist.watchlistId(selectedGroup)
        TvChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name ->
                Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show()
            },
            extraItems = if (watchlistId != null && watchlists.firstOrNull { it.id == watchlistId }?.isFavorites == false) {
                listOf(TvMenuItem(stringResource(R.string.remove_from_watchlist)) { vm.edits.toggleWatchlist(watchlistId, channel) })
            } else {
                emptyList()
            },
        )
    }
}

/**
 * Channels of one list. Focus comes back to [lastPlayedKey] after the player closes;
 * each [listKey] gets its own scroll position starting at the top.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvChannelList(
    channels: List<Channel>,
    guide: Map<ChannelRef, NowNext>,
    health: Map<ChannelRef, HealthStatus>,
    favorites: Set<ChannelRef>,
    subtitle: (Channel) -> String?,
    listKey: String,
    lastPlayedKey: String?,
    onPlay: (Channel) -> Unit,
    onEdit: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        Text(
            stringResource(R.string.empty_group),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 40.dp, start = 12.dp),
        )
        return
    }
    val lastPlayedFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    val listState = remember(listKey) {
        val start = channels.indexOfFirst { it.stableKey == lastPlayedKey }
        LazyListState(firstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer { firstChannelFocus },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(channels, key = { _, it -> "${it.playlistId}|${it.stableKey}" }) { index, channel ->
            val requester = when {
                channel.stableKey == lastPlayedKey -> lastPlayedFocus
                index == 0 -> firstChannelFocus
                else -> null
            }
            ChannelRow(
                channel = channel,
                programme = guide[channel.ref],
                health = health[channel.ref],
                isFavorite = channel.ref in favorites,
                subtitle = subtitle(channel),
                onClick = { onPlay(channel) },
                onLongClick = { onEdit(channel) },
                modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) {
        if (lastPlayedKey != null && channels.any { it.stableKey == lastPlayedKey }) {
            lastPlayedFocus.requestFocusWhenReady()
        }
    }
}

@Composable
private fun GroupRow(
    name: String,
    count: Int,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = selected,
        onClick = onFocused,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text(formatCount(count), style = MaterialTheme.typography.labelLarge)
        },
    )
}

@Composable
internal fun ChannelRow(
    channel: Channel,
    programme: NowNext?,
    health: HealthStatus?,
    isFavorite: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        // The Menu key opens the same edit menu as a long press: not every remote makes long-press easy.
        modifier = modifier.onPreviewKeyEvent {
            if (it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                onLongClick()
                true
            } else {
                false
            }
        },
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(44.dp),
                )
                ChannelLogo(channel.name, channel.logoUrl, size = 40.dp)
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                HealthDot(health, Modifier.padding(start = 10.dp))
            }
        },
        // What's on beats the subtitle (group or playlist name).
        supportingContent = when {
            programme?.now != null -> { { NowLine(programme) } }
            subtitle != null -> { { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            else -> null
        },
        trailingContent = if (isFavorite) {
            { Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.label_favorite), modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}

/** Current programme title with a thin progress bar under it. */
@Composable
internal fun NowLine(programme: NowNext) {
    val now = programme.now ?: return
    Column {
        Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(4.dp))
        ProgramProgress(
            fraction = programme.progress(System.currentTimeMillis()),
            color = LocalContentColor.current,
            track = LocalContentColor.current.copy(alpha = 0.25f),
            modifier = Modifier.width(160.dp),
        )
    }
}
