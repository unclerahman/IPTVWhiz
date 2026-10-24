package com.iptvwhiz.app.ui.mobile

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
import com.iptvwhiz.app.data.db.ChannelEntity
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
    onPlay: (ChannelEntity, group: String?) -> Unit,
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
            TopLevel.Settings -> MobileSettings(onOpenRoute, contentModifier)
            else -> selected.emptyState()?.let { MobileEmptyState(it, contentModifier) }
        }
    }
}

@Composable
private fun MobileLiveContent(
    vm: LiveViewModel,
    onPlay: (ChannelEntity, group: String?) -> Unit,
    onAddPlaylist: () -> Unit,
    modifier: Modifier,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()

    if (playlists == null) return
    val active = playlist
    if (active == null) {
        MobileEmptyState(NoPlaylistText, modifier, actionLabel = stringResource(R.string.add_provider), onAction = onAddPlaylist)
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedGroup) { listState.scrollToItem(0) }

    Column(modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "\u0000all") {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { vm.selectGroup(null) },
                    label = { Text("${stringResource(R.string.all_channels)} · ${formatCount(active.channelCount)}") },
                )
            }
            items(groups, key = { it.name }) { group ->
                FilterChip(
                    selected = selectedGroup == group.name,
                    onClick = { vm.selectGroup(group.name) },
                    label = { Text("${group.name} · ${formatCount(group.channelCount)}") },
                )
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.stableKey }) { channel ->
                ListItem(
                    leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 44.dp) },
                    overlineContent = { Text(channel.number.toString()) },
                    headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (selectedGroup == null) {
                        { Text(channel.groupTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onPlay(channel, selectedGroup) },
                )
            }
        }
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
