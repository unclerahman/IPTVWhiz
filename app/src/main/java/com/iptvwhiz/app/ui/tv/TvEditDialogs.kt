package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.iptvwhiz.app.data.Watchlist
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.watchlistName
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.ui.state.ChannelEdits
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.requestFocusWhenReady

/** One choice in a [TvMenuDialog]. */
data class TvMenuItem(val label: String, val onClick: () -> Unit)

/** A list of actions as full-width buttons; the first one gets focus. Items close the dialog themselves. */
@Composable
fun TvMenuDialog(title: String, items: List<TvMenuItem>, onDismiss: () -> Unit, message: String? = null) {
    val first = remember { FocusRequester() }
    TvDialog(title = title, message = message, onDismiss = onDismiss) {
        items.forEachIndexed { index, item ->
            val modifier = Modifier
                .fillMaxWidth()
                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
            if (index == 0) {
                Button(onClick = item.onClick, modifier = modifier) { Text(item.label) }
            } else {
                OutlinedButton(onClick = item.onClick, modifier = modifier) { Text(item.label) }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
}

/** The menu for one channel. [onChangePosition] only where reordering makes sense (manage screen). */
@Composable
fun TvChannelMenu(
    channel: Channel,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onWatchlists: () -> Unit,
    onRename: () -> Unit,
    onMoveToGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onChangePosition: (() -> Unit)? = null,
    extraItems: List<TvMenuItem> = emptyList(),
) {
    val items = buildList {
        add(
            TvMenuItem(
                stringResource(if (isFavorite) R.string.channel_remove_favorite else R.string.channel_add_favorite),
                onToggleFavorite,
            ),
        )
        add(TvMenuItem(stringResource(R.string.add_to_watchlist), onWatchlists))
        if (onChangePosition != null) add(TvMenuItem(stringResource(R.string.change_position), onChangePosition))
        addAll(extraItems)
        add(TvMenuItem(stringResource(R.string.channel_rename), onRename))
        add(TvMenuItem(stringResource(R.string.channel_move_group), onMoveToGroup))
        add(TvMenuItem(stringResource(if (channel.hidden) R.string.channel_show else R.string.channel_hide), onToggleHidden))
    }
    TvMenuDialog(
        title = channel.name,
        message = if (channel.name != channel.originalName) stringResource(R.string.originally, channel.originalName) else null,
        items = items,
        onDismiss = onDismiss,
    )
}

/** Tick or untick the watchlists a channel is in; "New watchlist…" creates one with it. */
@Composable
fun TvWatchlistPickerDialog(
    channel: Channel,
    watchlists: List<Watchlist>,
    onDismiss: () -> Unit,
    onToggle: (Watchlist) -> Unit,
    onNewWatchlist: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TvTextEntryDialog(
            title = stringResource(R.string.new_watchlist),
            label = stringResource(R.string.field_watchlist_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = onDismiss,
            onSave = { if (it.isNotBlank()) onNewWatchlist(it) },
        )
        return
    }
    val first = remember { FocusRequester() }
    TvDialog(title = stringResource(R.string.add_to_watchlist_title, channel.name), onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(watchlists, key = { it.id }) { list ->
                val inList = list.contains(channel.ref)
                ListItem(
                    selected = inList,
                    onClick = { onToggle(list) },
                    modifier = if (list == watchlists.first()) Modifier.focusRequester(first) else Modifier,
                    leadingContent = { Icon(if (inList) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null) },
                    headlineContent = { Text(watchlistName(list), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = { naming = true },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.new_watchlist)) },
                )
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
}

/** Single-field text entry with Save and Cancel; the keyboard opens straight away. */
@Composable
fun TvTextEntryDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    hint: String? = null,
    saveLabel: String = stringResource(R.string.save),
) {
    var text by remember { mutableStateOf(initial) }
    TvDialog(title = title, message = hint, onDismiss = onDismiss) {
        TvTextField(
            label = label,
            value = text,
            onValueChange = { text = it },
            keyboardType = KeyboardType.Text,
            startEditing = true,
        )
        Button(onClick = { onDismiss(); onSave(text) }, modifier = Modifier.fillMaxWidth()) { Text(saveLabel) }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

/** Pick a target group, or make a new one. */
@Composable
fun TvGroupPickerDialog(
    groups: List<ChannelGroup>,
    currentKey: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onNewGroup: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TvTextEntryDialog(
            title = stringResource(R.string.new_group),
            label = stringResource(R.string.field_group_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = onDismiss,
            onSave = { if (it.isNotBlank()) onNewGroup(it) },
        )
        return
    }
    val currentFocus = remember { FocusRequester() }
    TvDialog(title = stringResource(R.string.move_to_group_title), onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            item {
                ListItem(
                    selected = false,
                    onClick = { naming = true },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.new_group)) },
                )
            }
            items(groups, key = { it.key }) { group ->
                ListItem(
                    selected = group.key == currentKey,
                    onClick = { onDismiss(); onPick(group.key) },
                    modifier = if (group.key == currentKey) Modifier.focusRequester(currentFocus) else Modifier,
                    headlineContent = { Text(group.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { currentFocus.requestFocusWhenReady() }
}

private enum class EditStage { Menu, Rename, Group, Watchlists }

/**
 * The whole "edit this channel" flow for the remote: menu → rename / move to group / watchlists.
 * Works for a channel from any playlist. [onDone] closes it. [onChangePosition] adds
 * "Change position" (manage screens); [extraItems] adds context entries (e.g. "Remove from this watchlist").
 */
@Composable
fun TvChannelEditor(
    channel: Channel,
    watchlists: List<Watchlist>,
    edits: ChannelEdits,
    onDone: () -> Unit,
    onNameTaken: (String) -> Unit,
    onChangePosition: (() -> Unit)? = null,
    extraItems: List<TvMenuItem> = emptyList(),
) {
    var stage by remember(channel.stableKey) { mutableStateOf(EditStage.Menu) }
    val isFavorite = watchlists.firstOrNull { it.isFavorites }?.contains(channel.ref) == true
    when (stage) {
        EditStage.Menu -> TvChannelMenu(
            channel = channel,
            isFavorite = isFavorite,
            onDismiss = onDone,
            onToggleFavorite = { edits.toggleFavorite(channel); onDone() },
            onWatchlists = { stage = EditStage.Watchlists },
            onRename = { stage = EditStage.Rename },
            onMoveToGroup = { stage = EditStage.Group },
            onToggleHidden = { edits.setHidden(channel, !channel.hidden); onDone() },
            onChangePosition = onChangePosition?.let { change -> { onDone(); change() } },
            extraItems = extraItems.map { item -> item.copy(onClick = { onDone(); item.onClick() }) },
        )
        EditStage.Watchlists -> TvWatchlistPickerDialog(
            channel = channel,
            watchlists = watchlists,
            onDismiss = onDone,
            onToggle = { edits.toggleWatchlist(it.id, channel) },
            onNewWatchlist = { name -> edits.addToNewWatchlist(name, channel) { onNameTaken(name) } },
        )
        EditStage.Rename -> TvTextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_name),
            hint = stringResource(R.string.rename_hint),
            initial = channel.name,
            onDismiss = onDone,
            onSave = { edits.rename(channel, it) },
        )
        EditStage.Group -> {
            val groups by produceState<List<ChannelGroup>?>(null, channel.playlistId) { value = edits.groupsFor(channel) }
            groups?.let { list ->
                TvGroupPickerDialog(
                    groups = list,
                    currentKey = channel.groupTitle,
                    onDismiss = onDone,
                    onPick = { edits.moveToGroup(channel, it) },
                    onNewGroup = { name -> edits.moveToNewGroup(channel, name) { onNameTaken(name) } },
                )
            }
        }
    }
}
