package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.iptvwhiz.app.ui.common.HealthBadge
import com.iptvwhiz.app.ui.common.NoPlaylistText
import com.iptvwhiz.app.ui.common.NoWatchlistsText
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.SettingsEntry
import com.iptvwhiz.app.ui.common.SettingsSection
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
                    Column {
                        Text(
                            text = if (selected == TopLevel.Live) {
                                stringResource(R.string.nav_live)
                            } else {
                                stringResource(selected.label)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected == TopLevel.Live) {
                            Text(
                                text = playlist?.name ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { selected = TopLevel.Search }) {
                        Icon(
                            TopLevel.Search.icon,
                            contentDescription = stringResource(R.string.nav_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                bottomBarSections.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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

    if (playlists == null) {
        MobileLoading(modifier)
        return
    }
    if (playlist == null) {
        MobileEmptyState(NoPlaylistText, modifier, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }
    if (watchlistsOnly && watchlists.all { it.entries.isEmpty() }) {
        MobileEmptyState(NoWatchlistsText, modifier, icon = Icons.Filled.Favorite)
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
    // A group name under every row is noise when the whole list is from one group.
    val mixedGroups = remember(channels) { channels.map { it.groupTitle }.distinct().size > 1 }

    Column(modifier) {
        LazyRow(
            state = chipState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "All channels" first: it is the default view and the one people return to.
            if (!watchlistsOnly) {
                item(key = OrganizedPlaylist.ALL) {
                    BrowseChip(
                        label = stringResource(R.string.all_channels),
                        count = organized?.visibleCount ?: 0,
                        selected = selectedGroup == OrganizedPlaylist.ALL,
                        onClick = { vm.selectGroup(OrganizedPlaylist.ALL) },
                    )
                }
                items(groups, key = { it.key }) { group ->
                    BrowseChip(
                        label = group.displayName,
                        count = group.visibleCount,
                        selected = selectedGroup == group.key,
                        onClick = { vm.selectGroup(group.key) },
                    )
                }
            }
            items(shownLists, key = { OrganizedPlaylist.watchlistKey(it.id) }) { list ->
                BrowseChip(
                    label = watchlistName(list),
                    count = list.available.size,
                    selected = selectedGroup == OrganizedPlaylist.watchlistKey(list.id),
                    onClick = { vm.selectGroup(OrganizedPlaylist.watchlistKey(list.id)) },
                )
            }
        }
        MobileChannelList(
            channels = channels,
            guide = guide,
            health = health,
            favorites = favorites,
            subtitle = { channel ->
                when {
                    watchlistId != null && names.size > 1 ->
                        "${names[channel.playlistId].orEmpty()} · ${channel.groupTitle}"
                    watchlistId != null -> if (mixedGroups) channel.groupTitle else null
                    selectedGroup == OrganizedPlaylist.ALL -> if (mixedGroups) channel.groupTitle else null
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

/** A filter chip with the number of channels in a quieter second weight. */
@Composable
private fun BrowseChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Text(
                    formatCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun MobileLoading(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.loading_channels),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        itemsIndexed(channels, key = { _, it -> "${it.playlistId}|${it.stableKey}" }) { index, channel ->
            val programme = guide[channel.ref]
            val sub = subtitle(channel)
            Column {
                ListItem(
                    leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = 24.dp),
                            )
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            HealthBadge(health[channel.ref], Modifier.padding(start = 6.dp))
                            if (channel.ref in favorites) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = stringResource(R.string.label_favorite),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                )
                            }
                        }
                    },
                    supportingContent = when {
                        programme?.now != null -> { { MobileNowLine(programme) } }
                        sub != null -> { { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                        else -> null
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onPlay(channel) },
                        onLongClick = { onLongPress(channel) },
                    ),
                )
                if (index < channels.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MobileEmptyState(
    text: EmptyStateText,
    modifier: Modifier,
    icon: ImageVector = Icons.Filled.PlayArrow,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(text.title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(text.message),
            style = MaterialTheme.typography.bodyMedium,
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
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        SettingsSection.entries.forEach { section ->
            val entries = settingsEntries.filter { it.section == section }
            if (entries.isEmpty()) return@forEach
            item(key = "section-${section.name}") {
                Text(
                    text = stringResource(section.title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
                )
            }
            items(entries, key = { it.title }) { entry ->
                SettingsRow(entry, onOpenRoute)
            }
        }
        item { AdSlot() }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    // About is an information row: version in the supporting line, no chevron and no dead tap.
    val isAbout = entry.title == R.string.settings_about
    val soon = entry.route == null && !isAbout
    val onClick: (() -> Unit)? = when {
        entry.route != null -> ({ onOpenRoute(entry.route) })
        isAbout -> null
        else -> ({ context.showComingSoon() })
    }
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = {
            Text(stringResource(entry.title), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        },
        supportingContent = {
            Text(stringResource(entry.description, BuildConfig.VERSION_NAME), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = when {
            soon -> {
                {
                    Text(
                        stringResource(R.string.settings_soon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            entry.route != null -> {
                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> null
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun MobileNowLine(programme: NowNext) {
    val now = programme.now ?: return
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.formatTimeRange(now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(now.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(5.dp))
        ProgramProgress(
            fraction = programme.progress(System.currentTimeMillis()),
            color = MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
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
        MobileEmptyState(
            EmptyStateText(R.string.guide_empty_title, R.string.guide_empty_message),
            modifier,
            icon = Icons.Filled.DateRange,
        )
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(channels, key = { _, it -> it.stableKey }) { index, channel ->
            val programme = guide[channel.ref]
            Column {
                ListItem(
                    leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                    overlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = 24.dp),
                            )
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            programme?.now?.let { "${context.formatTimeRange(it)}  ${it.title}" }
                                ?: stringResource(R.string.guide_nothing_now),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
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
                if (index < channels.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
