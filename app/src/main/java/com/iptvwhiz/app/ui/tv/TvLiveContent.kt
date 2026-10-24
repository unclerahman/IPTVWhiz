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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.NoFavoritesText
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/**
 * TiviMate-style browser: groups on the left, the group's channels on the right.
 * Moving over a group shows its channels straight away; OK plays, long-press OK or Menu edits.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLiveContent(onPlay: (Channel, group: String) -> Unit, onAddPlaylist: () -> Unit) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val organized by vm.organized.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()

    if (playlists == null) return
    val active = playlist
    if (active == null) {
        TvEmptyState(NoPlaylistText, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }

    var lastPlayedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    val firstGroupFocus = remember { FocusRequester() }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text(
                text = active.name,
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
                val favorites = organized?.favorites.orEmpty()
                if (favorites.isNotEmpty()) {
                    item(key = OrganizedPlaylist.FAVORITES) {
                        GroupRow(
                            name = stringResource(R.string.nav_favorites),
                            count = favorites.size,
                            selected = selectedGroup == OrganizedPlaylist.FAVORITES,
                            onFocused = { vm.selectGroup(OrganizedPlaylist.FAVORITES) },
                        )
                    }
                }
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
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            TvChannelList(
                channels = channels,
                listKey = selectedGroup,
                showGroup = selectedGroup == OrganizedPlaylist.ALL || selectedGroup == OrganizedPlaylist.FAVORITES,
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
        TvChannelEditor(
            channel = channel,
            organized = organized,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name ->
                Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** The Favorites section: just the favorite channels, playable and editable. */
@Composable
fun TvFavoritesContent(onPlay: (Channel, group: String) -> Unit) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val context = LocalContext.current
    val organized by vm.organized.collectAsStateWithLifecycle()
    var lastPlayedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    val favorites = organized?.favorites ?: return

    if (favorites.isEmpty()) {
        TvEmptyState(NoFavoritesText)
        return
    }
    Column {
        Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(16.dp))
        TvChannelList(
            channels = favorites,
            listKey = OrganizedPlaylist.FAVORITES,
            showGroup = true,
            lastPlayedKey = lastPlayedKey,
            onPlay = { channel ->
                lastPlayedKey = channel.stableKey
                onPlay(channel, OrganizedPlaylist.FAVORITES)
            },
            onEdit = { editing = it },
        )
    }
    editing?.let { channel ->
        TvChannelEditor(
            channel = channel,
            organized = organized,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name ->
                Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show()
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
private fun TvChannelList(
    channels: List<Channel>,
    listKey: String,
    showGroup: Boolean,
    lastPlayedKey: String?,
    onPlay: (Channel) -> Unit,
    onEdit: (Channel) -> Unit,
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
        modifier = Modifier.focusRestorer { firstChannelFocus },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(channels, key = { _, it -> it.stableKey }) { index, channel ->
            val requester = when {
                channel.stableKey == lastPlayedKey -> lastPlayedFocus
                index == 0 -> firstChannelFocus
                else -> null
            }
            ChannelRow(
                channel = channel,
                showGroup = showGroup,
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
private fun ChannelRow(
    channel: Channel,
    showGroup: Boolean,
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
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        // The group name only adds information in mixed lists; elsewhere it costs rows on screen.
        supportingContent = if (showGroup) {
            { Text(channel.groupTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (channel.favorite) {
            { Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.label_favorite), modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}
