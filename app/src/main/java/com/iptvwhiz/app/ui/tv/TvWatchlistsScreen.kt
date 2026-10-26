package com.iptvwhiz.app.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.WatchlistEntry
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.HealthDot
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.common.watchlistName
import com.iptvwhiz.app.ui.state.WatchlistsViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea
import kotlinx.coroutines.launch

/** Settings → Watchlists with the remote: lists left, their channels right; OK opens a menu. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvWatchlistsScreen() {
    val vm = appViewModel { container, savedState -> WatchlistsViewModel(container, savedState) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.nameTaken.collect { Toast.makeText(context, context.getString(R.string.name_taken, it), Toast.LENGTH_SHORT).show() }
    }

    val current = watchlists.firstOrNull { it.id == selected } ?: watchlists.firstOrNull()
    var listMenu by remember { mutableStateOf<Watchlist?>(null) }
    var entryMenu by remember { mutableStateOf<WatchlistEntry?>(null) }
    var renaming by remember { mutableStateOf<Watchlist?>(null) }
    var deleting by remember { mutableStateOf<Watchlist?>(null) }
    var creating by remember { mutableStateOf(false) }

    var movingLists by remember { mutableStateOf<List<Watchlist>?>(null) }
    var movingListId by remember { mutableStateOf<Long?>(null) }
    var movingEntries by remember { mutableStateOf<List<WatchlistEntry>?>(null) }
    var movingEntryKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(watchlists) { if (movingListId == null) movingLists = null }
    LaunchedEffect(entries) { if (movingEntryKey == null) movingEntries = null }
    BackHandler(enabled = movingListId != null) { movingListId = null; movingLists = null }
    BackHandler(enabled = movingEntryKey != null) { movingEntryKey = null; movingEntries = null }

    val listsState = rememberLazyListState()
    val entriesState = remember(current?.id) { LazyListState() }
    val firstList = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstList.requestFocusWhenReady() }

    fun keepVisible(state: LazyListState, index: Int) {
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        if (index <= visible.first().index || index >= visible.last().index) {
            scope.launch { state.scrollToItem((index - 3).coerceAtLeast(0)) }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_watchlists), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (movingListId != null || movingEntryKey != null) {
                Text(stringResource(R.string.move_hint_tv), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            } else {
                OutlinedButton(onClick = { creating = true }) { Text(stringResource(R.string.new_watchlist)) }
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listsState,
                modifier = Modifier.width(380.dp).fillMaxHeight().focusRestorer { firstList },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val shown = movingLists ?: watchlists
                itemsIndexed(shown, key = { _, it -> it.id }) { index, list ->
                    val moving = list.id == movingListId
                    ListItem(
                        selected = list.id == current?.id,
                        onClick = {
                            if (moving) {
                                movingListId = null
                                vm.moveList(shown, list)
                            } else {
                                listMenu = list
                            }
                        },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstList) else Modifier)
                            .onFocusChanged { if (it.isFocused && movingListId == null) vm.select(list.id) }
                            .moveKeys(moving) { delta ->
                                val to = index + delta
                                val now = movingLists ?: return@moveKeys
                                if (to in now.indices) {
                                    movingLists = now.toMutableList().apply { add(to, removeAt(index)) }
                                    keepVisible(listsState, to)
                                }
                            },
                        border = if (moving) movingBorder() else ListItemDefaults.border(),
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                        headlineContent = { Text(watchlistName(list), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.watchlist_channel_count, list.entries.size, list.entries.size))
                        },
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (current != null && current.entries.isNotEmpty()) {
                    TvHealthBar(vm.healthCheck, current.available, Modifier.padding(start = 12.dp, bottom = 8.dp))
                }
                if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.watchlist_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                LazyColumn(state = entriesState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val shown = movingEntries ?: entries
                    itemsIndexed(shown, key = { _, it -> "${it.item.playlistId}|${it.item.stableKey}" }) { index, entry ->
                        val key = "${entry.item.playlistId}|${entry.item.stableKey}"
                        val moving = key == movingEntryKey
                        val channel = entry.channel
                        ListItem(
                            selected = false,
                            onClick = {
                                if (moving) {
                                    movingEntryKey = null
                                    current?.let { vm.moveEntry(it.id, shown, entry) }
                                } else {
                                    entryMenu = entry
                                }
                            },
                            modifier = Modifier
                                .alpha(if (channel == null && !moving) 0.5f else 1f)
                                .moveKeys(moving) { delta ->
                                    val to = index + delta
                                    val now = movingEntries ?: return@moveKeys
                                    if (to in now.indices) {
                                        movingEntries = now.toMutableList().apply { add(to, removeAt(index)) }
                                        keepVisible(entriesState, to)
                                    }
                                },
                            border = if (moving) movingBorder() else ListItemDefaults.border(),
                            scale = ListItemDefaults.scale(focusedScale = 1.02f),
                            leadingContent = { ChannelLogo(channel?.name ?: "?", channel?.logoUrl, size = 40.dp) },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        channel?.name ?: stringResource(R.string.watchlist_unavailable),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    HealthDot(health[entry.ref], Modifier.padding(start = 10.dp))
                                }
                            },
                            supportingContent = { Text(entry.playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
    }

    listMenu?.let { list ->
        val close = { listMenu = null }
        TvMenuDialog(
            title = watchlistName(list),
            onDismiss = close,
            items = buildList {
                add(TvMenuItem(stringResource(R.string.change_position)) { close(); movingLists = watchlists; movingListId = list.id })
                if (!list.builtIn) {
                    add(TvMenuItem(stringResource(R.string.channel_rename)) { close(); renaming = list })
                    add(TvMenuItem(stringResource(R.string.watchlist_delete)) { close(); deleting = list })
                }
            },
        )
    }
    entryMenu?.let { entry ->
        val close = { entryMenu = null }
        TvMenuDialog(
            title = entry.channel?.name ?: stringResource(R.string.watchlist_unavailable),
            message = entry.playlistName,
            onDismiss = close,
            items = listOf(
                TvMenuItem(stringResource(R.string.change_position)) { close(); movingEntries = entries; movingEntryKey = "${entry.item.playlistId}|${entry.item.stableKey}" },
                TvMenuItem(stringResource(R.string.remove_from_watchlist)) { close(); current?.let { vm.remove(it.id, entry) } },
            ),
        )
    }
    if (creating) {
        TvTextEntryDialog(
            title = stringResource(R.string.new_watchlist),
            label = stringResource(R.string.field_watchlist_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = { creating = false },
            onSave = { if (it.isNotBlank()) vm.create(it) },
        )
    }
    renaming?.let { list ->
        TvTextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_watchlist_name),
            initial = list.name,
            onDismiss = { renaming = null },
            onSave = { vm.rename(list, it) },
        )
    }
    deleting?.let { list ->
        val cancelFocus = remember { FocusRequester() }
        TvDialog(
            title = stringResource(R.string.watchlist_delete_confirm_title, list.name),
            message = stringResource(R.string.watchlist_delete_confirm_message),
            onDismiss = { deleting = null },
        ) {
            Button(onClick = { deleting = null }, modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus)) {
                Text(stringResource(R.string.cancel))
            }
            OutlinedButton(onClick = { vm.delete(list); deleting = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.watchlist_delete))
            }
        }
        LaunchedEffect(list.id) { cancelFocus.requestFocusWhenReady() }
    }
}

/** In move mode the focused row takes ▲▼ itself instead of moving focus. */
internal fun Modifier.moveKeys(active: Boolean, onMove: (Int) -> Unit): Modifier =
    if (!active) {
        this
    } else {
        onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent it.key == Key.DirectionUp || it.key == Key.DirectionDown
            when (it.key) {
                Key.DirectionUp -> { onMove(-1); true }
                Key.DirectionDown -> { onMove(1); true }
                // Sideways would leave the row mid-move.
                Key.DirectionLeft, Key.DirectionRight -> true
                else -> false
            }
        }
    }

@Composable
internal fun movingBorder() = ListItemDefaults.border(
    border = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.secondary), shape = RoundedCornerShape(12.dp)),
    focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.secondary), shape = RoundedCornerShape(12.dp)),
)
