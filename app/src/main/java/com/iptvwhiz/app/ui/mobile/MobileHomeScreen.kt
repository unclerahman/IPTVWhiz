package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.nextLine
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.remember
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.ui.common.NoFavoritesText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.TopLevel
import com.iptvwhiz.app.ui.common.AdSlot
import com.iptvwhiz.app.ui.common.BackToExit
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.emptyState
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.settingsEntries
import com.iptvwhiz.app.ui.common.showComingSoon
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Search lives in the top bar on phones, so the bottom bar shows the other four sections. */
private val bottomBarSections = TopLevel.entries - TopLevel.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onPlay: (Channel, group: String) -> Unit,
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
            TopLevel.Live -> MobileLiveContent(vm, onPlay, onAddPlaylist, contentModifier)
            TopLevel.Favorites -> MobileFavoritesContent(vm, onPlay, contentModifier)
            TopLevel.Guide -> MobileGuideContent(vm, onPlay, contentModifier)
            TopLevel.Settings -> MobileSettings(onOpenRoute, contentModifier)
            else -> selected.emptyState()?.let { MobileEmptyState(it, contentModifier) }
        }
    }
}

@Composable
private fun MobileLiveContent(
    vm: LiveViewModel,
    onPlay: (Channel, group: String) -> Unit,
    onAddPlaylist: () -> Unit,
    modifier: Modifier,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val organized by vm.organized.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()

    if (playlists == null) return
    if (playlist == null) {
        MobileEmptyState(NoPlaylistText, modifier, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedGroup) { listState.scrollToItem(0) }
    // The Favorites chip appears in front of "All channels"; scroll so it's seen, not hidden off the left edge.
    val chipState = rememberLazyListState()
    val hasFavorites = !organized?.favorites.isNullOrEmpty()
    LaunchedEffect(hasFavorites) { if (hasFavorites) chipState.scrollToItem(0) }

    Column(modifier) {
        LazyRow(
            state = chipState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val favorites = organized?.favorites.orEmpty()
            if (favorites.isNotEmpty()) {
                item(key = OrganizedPlaylist.FAVORITES) {
                    FilterChip(
                        selected = selectedGroup == OrganizedPlaylist.FAVORITES,
                        onClick = { vm.selectGroup(OrganizedPlaylist.FAVORITES) },
                        label = { Text("${stringResource(R.string.nav_favorites)} · ${formatCount(favorites.size)}") },
                    )
                }
            }
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
        MobileChannelList(
            vm = vm,
            channels = channels,
            showGroup = selectedGroup == OrganizedPlaylist.ALL || selectedGroup == OrganizedPlaylist.FAVORITES,
            listState = listState,
            onPlay = { onPlay(it, selectedGroup) },
        )
    }
}

@Composable
private fun MobileFavoritesContent(vm: LiveViewModel, onPlay: (Channel, group: String) -> Unit, modifier: Modifier) {
    val organized by vm.organized.collectAsStateWithLifecycle()
    val favorites = organized?.favorites ?: return
    if (favorites.isEmpty()) {
        MobileEmptyState(NoFavoritesText, modifier)
        return
    }
    Box(modifier) {
        MobileChannelList(
            vm = vm,
            channels = favorites,
            showGroup = true,
            listState = rememberLazyListState(),
            onPlay = { onPlay(it, OrganizedPlaylist.FAVORITES) },
        )
    }
}

/** Tap plays; long-press opens the edit sheet (favorite, rename, move, hide). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileChannelList(
    vm: LiveViewModel,
    channels: List<Channel>,
    showGroup: Boolean,
    listState: LazyListState,
    onPlay: (Channel) -> Unit,
) {
    val context = LocalContext.current
    val organized by vm.organized.collectAsStateWithLifecycle()
    val guide by vm.nowNext.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Channel?>(null) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(channels, key = { it.stableKey }) { channel ->
            val programme = guide[channel.stableKey]
            ListItem(
                leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                overlineContent = { Text(channel.number.toString()) },
                headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = when {
                    programme?.now != null -> { { MobileNowLine(programme) } }
                    showGroup -> { { Text(channel.groupTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    else -> null
                },
                trailingContent = if (channel.favorite) {
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
                    onLongClick = { editing = channel },
                ),
            )
        }
    }
    editing?.let { channel ->
        ChannelEditor(
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

@Composable
private fun MobileEmptyState(
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
        Text(stringResource(text.title), style = MaterialTheme.typography.titleLarge)
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
    val guide by vm.nowNext.collectAsStateWithLifecycle()
    val channels = remember(organized, guide) {
        organized?.browse(OrganizedPlaylist.ALL).orEmpty().filter { guide[it.stableKey] != null }
    }
    if (channels.isEmpty()) {
        MobileEmptyState(EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message), modifier)
        return
    }
    LazyColumn(modifier) {
        items(channels, key = { it.stableKey }) { channel ->
            val programme = guide[channel.stableKey]
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
