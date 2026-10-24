package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.state.ManageEvent
import com.iptvwhiz.app.ui.state.ManageViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Groups of the active playlist: drag ≡ to reorder, switch to show/hide, tap to edit its channels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileManageScreen(onOpenGroup: (String) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> ManageViewModel(container, savedState) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChannelGroup?>(null) }
    var deleting by remember { mutableStateOf<ChannelGroup?>(null) }
    NameTakenToasts(vm)

    // Local copy while dragging; replaced by the database's order when it changes. One state object
    // for the screen's lifetime, because the drag handlers keep a reference to it.
    var local by remember { mutableStateOf(groups) }
    LaunchedEffect(groups) { local = groups }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState)

    Scaffold(
        topBar = { ManageTopBar(stringResource(R.string.manage_title), onBack) },
        floatingActionButton = {
            if (playlist != null) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_group)) },
                )
            }
        },
    ) { padding ->
        if (playlist == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.manage_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.move_hint_phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(local, key = { it.key }) { group ->
                var menuOpen by remember { mutableStateOf(false) }
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = null,
                            modifier = Modifier.dragHandle(
                                reorder,
                                group.key,
                                // Item 0 is the hint row, so list indices are one ahead of the groups.
                                onMove = { from, to -> if (from >= 1 && to >= 1) local = local.moved(from - 1, to - 1) },
                                onDrop = { key -> local.firstOrNull { it.key == key }?.let { vm.moveGroup(local, it) } },
                            ),
                        )
                    },
                    headlineContent = { Text(group.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(groupDetails(group), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = !group.hidden, onCheckedChange = { vm.setGroupHidden(group, !it) })
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.channel_rename)) },
                                        onClick = { menuOpen = false; renaming = group },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.group_sort_az)) },
                                        onClick = { menuOpen = false; vm.sortGroupAlphabetically(group) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.group_reset_order)) },
                                        onClick = { menuOpen = false; vm.resetGroupOrder(group) },
                                    )
                                    if (group.isCustom) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.group_delete)) },
                                            onClick = { menuOpen = false; deleting = group },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .draggableRow(reorder, group.key)
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(if (group.hidden) 0.6f else 1f)
                        .clickable { onOpenGroup(group.key) },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        TextEntryDialog(
            title = stringResource(R.string.new_group),
            label = stringResource(R.string.field_group_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = { creating = false },
            onSave = { if (it.isNotBlank()) vm.createGroup(it) },
        )
    }
    renaming?.let { group ->
        TextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_name),
            hint = if (group.isCustom) null else stringResource(R.string.rename_hint),
            initial = group.displayName,
            onDismiss = { renaming = null },
            onSave = { vm.renameGroup(group, it) },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.group_delete_confirm_title, group.displayName)) },
            text = { Text(stringResource(R.string.group_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteGroup(group); deleting = null }) {
                    Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Channels of one group: drag ≡ to reorder, switch to show/hide, heart for favorites, tap for more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileManageGroupScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> ManageViewModel(container, savedState) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val organized by vm.organized.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Channel?>(null) }
    NameTakenToasts(vm)

    var local by remember { mutableStateOf(channels) }
    LaunchedEffect(channels) { local = channels }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState)
    val group = groups.firstOrNull { it.key == selectedGroup }

    Scaffold(topBar = { ManageTopBar(group?.displayName.orEmpty(), onBack) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            items(local, key = { it.stableKey }) { channel ->
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
                                        channel.stableKey,
                                        onMove = { from, to -> local = local.moved(from, to) },
                                        onDrop = { key -> local.firstOrNull { it.stableKey == key }?.let { vm.moveChannel(local, it) } },
                                    ),
                            )
                            ChannelLogo(channel.name, channel.logoUrl, size = 40.dp)
                        }
                    },
                    headlineContent = { Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (channel.name != channel.originalName) {
                        { Text(stringResource(R.string.originally, channel.originalName), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.edits.toggleFavorite(channel) }) {
                                Icon(
                                    if (channel.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(R.string.label_favorite),
                                    tint = if (channel.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = !channel.hidden, onCheckedChange = { vm.edits.setHidden(channel, !it) })
                        }
                    },
                    modifier = Modifier
                        .draggableRow(reorder, channel.stableKey)
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(if (channel.hidden) 0.6f else 1f)
                        .clickable { editing = channel },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { channel ->
        ChannelEditor(
            channel = channel,
            organized = organized,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = vm::nameTaken,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
    )
}

@Composable
private fun NameTakenToasts(vm: ManageViewModel) {
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            if (event is ManageEvent.NameTaken) {
                Toast.makeText(context, context.getString(R.string.name_taken, event.name), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun groupDetails(group: ChannelGroup): String = buildList {
    if (group.isCustom) add(stringResource(R.string.label_your_group))
    add(
        stringResource(
            R.string.group_counts,
            formatCount(group.visibleCount),
            formatCount(group.totalCount - group.visibleCount),
        ),
    )
}.joinToString(" · ")

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from in indices && to in indices) toMutableList().apply { add(to, removeAt(from)) } else this
