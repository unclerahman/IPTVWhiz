package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.ChannelEntity
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/**
 * TiviMate-style browser: groups on the left, the group's channels on the right.
 * Moving over a group shows its channels straight away; OK on a channel plays it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLiveContent(onPlay: (ChannelEntity, group: String?) -> Unit, onAddPlaylist: () -> Unit) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()

    if (playlists == null) return
    val active = playlist
    if (active == null) {
        TvEmptyState(NoPlaylistText, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }

    // Coming back from the player puts focus on the channel that was playing.
    var lastPlayedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val lastPlayedFocus = remember { FocusRequester() }
    val firstGroupFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }

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
                item(key = "\u0000all") {
                    GroupRow(
                        name = stringResource(R.string.all_channels),
                        count = active.channelCount,
                        selected = selectedGroup == null,
                        onFocused = { vm.selectGroup(null) },
                        modifier = Modifier.focusRequester(firstGroupFocus),
                    )
                }
                items(groups, key = { it.name }) { group ->
                    GroupRow(
                        name = group.name,
                        count = group.channelCount,
                        selected = selectedGroup == group.name,
                        onFocused = { vm.selectGroup(group.name) },
                    )
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (channels.isEmpty()) {
                Text(
                    stringResource(R.string.empty_group),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 40.dp, start = 12.dp),
                )
            } else {
                // A new list per group, so the scroll position starts at the top of each group.
                val listState = remember(selectedGroup) {
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
                            showGroup = selectedGroup == null,
                            onClick = {
                                lastPlayedKey = channel.stableKey
                                onPlay(channel, selectedGroup)
                            },
                            modifier = if (requester != null) Modifier.focusRequester(requester) else Modifier,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (lastPlayedKey != null) lastPlayedFocus.requestFocusWhenReady()
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
private fun ChannelRow(channel: ChannelEntity, showGroup: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
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
        // The group name only adds information in "All channels"; elsewhere it costs rows on screen.
        supportingContent = if (showGroup) {
            { Text(channel.groupTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
    )
}
