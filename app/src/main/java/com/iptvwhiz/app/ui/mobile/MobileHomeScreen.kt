package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelRef
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.TopLevel
import com.iptvwhiz.app.ui.common.AdSlot
import com.iptvwhiz.app.ui.common.BackToExit
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.HealthDot
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.NoWatchlistsText
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.nextLine
import com.iptvwhiz.app.ui.common.settingsEntries
import com.iptvwhiz.app.ui.common.showComingSoon
import com.iptvwhiz.app.ui.common.watchlistName
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Search lives in the top bar on phones, so the bottom bar shows the other four sections. */
private val bottomBarSections = TopLevel.entries - TopLevel.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onPlay: (Channel, browseKey: String) -> Unit,
    onPlayQueue: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(TopLevel.Live) }

    // Back: other section → Live TV → "press again to exit". The later handler wins.
    BackToExit()
    BackHandler(enabled = selected != TopLevel.Live) { selected = TopLevel.Live }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selected == TopLevel.Live) {
                            playlist?.name ?: stringResource(R.string.app_name)
                        } else {
                            stringResource(selected.label)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { selected = TopLevel.Search }) {
                        Icon(TopLevel.Search.icon, contentDescription = stringResource(R.string.nav_search))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                bottomBarSections.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (selected) {
            TopLevel.Live -> MobileBrowse(vm, onPlay, onAddPlaylist, contentModifier, watchlistsOnly = false)
            TopLevel.Favorites -> MobileBrowse(vm, onPlay, onAddPlaylist, contentModifier, watchlistsOnly = true)
            TopLevel.Guide -> MobileGuideContent(vm, onPlay, contentModifier)
            TopLevel.Search -> MobileSearchContent(onPlayQueue, contentModifier)
            TopLevel.Settings -> MobileSettings(onOpenRoute, contentModifier)
        }
    }
}

/** Live TV (watchlists, All channels, groups) or just the watchlists, as chips over a channel list. */
@Composable
private fun MobileBrowse(
    vm: LiveViewModel,
    onPlay: (Channel, browseKey: String) -> Unit,
    onAddPlaylist: () -> Unit,
    modifier: Modifier,
    watchlistsOnly: Boolean,
) {
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
    if (playlist == null) {
        MobileEmptyState(NoPlaylistText, modifier, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }
    if (watchlistsOnly && watchlists.all { it.entries.isEmpty() }) {
        MobileEmptyState(NoWatchlistsText, modifier)
        return
    }
    val shownLists = if (watchlistsOnly) watchlists else watchlists.filter { it.entries.isNotEmpty() }
    LaunchedEffect(watchlistsOnly, shownLists.firstOrNull()?.id) {
        if (watchlistsOnly && OrganizedPlaylist.watchlistId(selectedGroup) == null) {
            shownLists.firstOrNull()?.let { vm.selectGroup(OrganizedPlaylist.watchlistKey(it.id)) }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedGroup) { listState.scrollToItem(0) }
    val chipState = rememberLazyListState()
    LaunchedEffect(shownLists.size) { chipState.scrollToItem(0) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    val names = playlists.orEmpty().associate { it.id to it.name }
    val watchlistId = OrganizedPlaylist.watchlistId(selectedGroup)

    Column(modifier) {
        LazyRow(
            state = chipState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shownLists, key = { OrganizedPlaylist.watchlistKey(it.id) }) { list ->
                val key = OrganizedPlaylist.watchlistKey(list.id)
                FilterChip(
                    selected = selectedGroup == key,
                    onClick = { vm.selectGroup(key) },
                    label = { Text("${watchlistName(list)} · ${formatCount(list.available.size)}") },
                )
            }
            if (!watchlistsOnly) {
                item(key = OrganizedPlaylist.ALL) {
                    FilterChip(
                        selected = selectedGroup == OrganizedPlaylist.ALL,
                        onClick = { vm.selectGroup(OrganizedPlaylist.ALL) },
                        label = { Text("${stringResource(R.string.all_channels)} · ${formatCount(organized?.visibleCount ?: 0)}") },
                    )
                }
                items(groups, key = { it.key }) { group ->
                    FilterChip(
                        selected = selectedGroup == group.key,
                        onClick = { vm.selectGroup(group.key) },
                        label = { Text("${group.displayName} · ${formatCount(group.visibleCount)}") },
                    )
                }
            }
        }
        MobileChannelList(
            channels = channels,
            guide = guide,
            health = health,
            favorites = favorites,
            subtitle = { channel ->
                when {
                    watchlistId != null && names.size > 1 -> "${names[channel.playlistId].orEmpty()} · ${channel.groupTitle}"
                    watchlistId != null || selectedGroup == OrganizedPlaylist.ALL -> channel.groupTitle
                    else -> null
                }
            },
            listState = listState,
            onPlay = { onPlay(it, selectedGroup) },
            onLongPress = { editing = it },
        )
    }
    editing?.let { channel ->
        val customList = watchlistId?.takeIf { id -> watchlists.firstOrNull { it.id == id }?.isFavorites == false }
        ChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name -> Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show() },
            extraActions = if (customList != null) {
                listOf(SheetAction(stringResource(R.string.remove_from_watchlist)) { vm.edits.toggleWatchlist(customList, channel) })
            } else {
                emptyList()
            },
        )
    }
}

/** Tap plays; long-press opens the edit sheet. Shared by browsing and search. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MobileChannelList(
    channels: List<Channel>,
    guide: Map<ChannelRef, NowNext>,
    health: Map<ChannelRef, HealthStatus>,
    favorites: Set<ChannelRef>,
    subtitle: (Channel) -> String?,
    listState: LazyListState,
    onPlay: (Channel) -> Unit,
    onLongPress: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(channels, key = { "${it.playlistId}|${it.stableKey}" }) { channel ->
            val programme = guide[channel.ref]
            val sub = subtitle(channel)
            ListItem(
                leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                overlineContent = { Text(channel.number.toString()) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        HealthDot(health[channel.ref], Modifier.padding(start = 8.dp))
                    }
                },
                supportingContent = when {
                    programme?.now != null -> { { MobileNowLine(programme) } }
                    sub != null -> { { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    else -> null
                },
                trailingContent = if (channel.ref in favorites) {
                    {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.label_favorite),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.combinedClickable(
                    onClick = { onPlay(channel) },
                    onLongClick = { onLongPress(channel) },
                ),
            )
        }
    }
}

@Composable
internal fun MobileEmptyState(
    text: EmptyStateText,
    modifier: Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(text.title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(text.message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun MobileSettings(onOpenRoute: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier) {
        items(settingsEntries) { entry ->
            ListItem(
                headlineContent = { Text(stringResource(entry.title)) },
                supportingContent = { Text(stringResource(entry.description, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.clickable { entry.route?.let(onOpenRoute) ?: context.showComingSoon() },
            )
            HorizontalDivider()
        }
        item { AdSlot() }
    }
}

@Composable
private fun MobileNowLine(programme: NowNext) {
    val now = programme.now ?: return
    Column {
        Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        ProgramProgress(
            fraction = programme.progress(System.currentTimeMillis()),
            color = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(140.dp),
        )
    }
}

/** Guide section: what's on now and next on every channel that has a guide. */
@Composable
private fun MobileGuideContent(vm: LiveViewModel, onPlay: (Channel, group: String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val organized by vm.organized.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val channels = remember(organized, guide) {
        organized?.browse(OrganizedPlaylist.ALL).orEmpty().filter { guide[it.ref] != null }
    }
    if (channels.isEmpty()) {
        MobileEmptyState(EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message), modifier)
        return
    }
    LazyColumn(modifier) {
        items(channels, key = { it.stableKey }) { channel ->
            val programme = guide[channel.ref]
            ListItem(
                leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                overlineContent = { Text("${channel.number}  ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                headlineContent = {
                    Text(
                        programme?.now?.let { "${context.formatTimeRange(it)}  ${it.title}" }
                            ?: stringResource(R.string.guide_nothing_now),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        ProgramProgress(
                            fraction = programme?.progress(System.currentTimeMillis()) ?: 0f,
                            color = MaterialTheme.colorScheme.primary,
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        programme?.next?.let { Text(context.nextLine(it), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                modifier = Modifier.clickable { onPlay(channel, OrganizedPlaylist.ALL) },
            )
            HorizontalDivider()
        }
    }
}
