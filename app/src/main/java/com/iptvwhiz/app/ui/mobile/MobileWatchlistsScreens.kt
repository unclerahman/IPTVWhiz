package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.HealthDot
import com.iptvwhiz.app.ui.common.watchlistName
import com.iptvwhiz.app.ui.state.WatchlistsViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Settings → Watchlists: drag ≡ to reorder lists, tap a list to organise its channels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileWatchlistsScreen(onOpenList: (Long) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> WatchlistsViewModel(container, savedState) }
    val context = LocalContext.current
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Watchlist?>(null) }
    var deleting by remember { mutableStateOf<Watchlist?>(null) }
    LaunchedEffect(Unit) {
        vm.nameTaken.collect { Toast.makeText(context, context.getString(R.string.name_taken, it), Toast.LENGTH_SHORT).show() }
    }

    var local by remember { mutableStateOf(watchlists) }
    LaunchedEffect(watchlists) { local = watchlists }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState)

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.nav_watchlists), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_watchlist)) },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            items(local, key = { it.id }) { list ->
                var menuOpen by remember { mutableStateOf(false) }
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = null,
                            modifier = Modifier.dragHandle(
                                reorder,
                                list.id,
                                onMove = { from, to -> local = local.toMutableList().apply { add(to, removeAt(from)) } },
                                onDrop = { id -> local.firstOrNull { it.id == id }?.let { vm.moveList(local, it) } },
                            ),
                        )
                    },
                    headlineContent = { Text(watchlistName(list), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(pluralStringResource(R.plurals.watchlist_channel_count, list.entries.size, list.entries.size)) },
                    trailingContent = if (!list.builtIn) {
                        {
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.channel_rename)) },
                                        onClick = { menuOpen = false; renaming = list },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.watchlist_delete)) },
                                        onClick = { menuOpen = false; deleting = list },
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .draggableRow(reorder, list.id)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenList(list.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        TextEntryDialog(
            title = stringResource(R.string.new_watchlist),
            label = stringResource(R.string.field_watchlist_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = { creating = false },
            onSave = { if (it.isNotBlank()) vm.create(it) },
        )
    }
    renaming?.let { list ->
        TextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_watchlist_name),
            initial = list.name,
            onDismiss = { renaming = null },
            onSave = { vm.rename(list, it) },
        )
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.watchlist_delete_confirm_title, list.name)) },
            text = { Text(stringResource(R.string.watchlist_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(list); deleting = null }) {
                    Text(stringResource(R.string.watchlist_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** One watchlist's channels: drag ≡ to reorder, ✕ to remove, check which still work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileWatchlistScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> WatchlistsViewModel(container, savedState) }
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val list = watchlists.firstOrNull { it.id == selected }

    var local by remember { mutableStateOf(entries) }
    LaunchedEffect(entries) { local = entries }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState)

    Scaffold(topBar = { BackTopBar(list?.let { watchlistName(it) }.orEmpty(), onBack) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "health") {
                if (list != null && list.available.isNotEmpty()) {
                    HealthBar(vm.healthCheck, list.available, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.watchlist_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(local, key = { "${it.item.playlistId}|${it.item.stableKey}" }) { entry ->
                val key = "${entry.item.playlistId}|${entry.item.stableKey}"
                val channel = entry.channel
                ListItem(
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .dragHandle(
                                        reorder,
                                        key,
                                        // Item 0 is the health bar, so list indices are one ahead.
                                        onMove = { from, to ->
                                            if (from >= 1 && to >= 1) local = local.toMutableList().apply { add(to - 1, removeAt(from - 1)) }
                                        },
                                        onDrop = { dropped ->
                                            val moved = local.firstOrNull { "${it.item.playlistId}|${it.item.stableKey}" == dropped }
                                            if (moved != null && list != null) vm.moveEntry(list.id, local, moved)
                                        },
                                    ),
                            )
                            ChannelLogo(channel?.name ?: "?", channel?.logoUrl, size = 40.dp)
                        }
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel?.name ?: stringResource(R.string.watchlist_unavailable),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            HealthDot(health[entry.ref], Modifier.padding(start = 8.dp))
                        }
                    },
                    supportingContent = { Text(entry.playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { list?.let { vm.remove(it.id, entry) } }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_from_watchlist))
                        }
                    },
                    modifier = Modifier
                        .draggableRow(reorder, key)
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(if (channel == null) 0.5f else 1f),
                )
                HorizontalDivider()
            }
        }
    }
}
