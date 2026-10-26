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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.HealthDot
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.EmptyStateText
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.ManageEvent
import com.iptvwhiz.app.ui.state.ManageViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea
import kotlinx.coroutines.launch

/**
 * Organize the active playlist with the remote. OK on a group or channel opens its menu.
 * "Change position" enters move mode: ▲▼ moves the item, OK places it, Back cancels.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvManageScreen() {
    val vm = appViewModel { container, savedState -> ManageViewModel(container, savedState) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by vm.activePlaylist.collectAsStateWithLifecycle()
    val organized by vm.organized.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    val favorites = watchlists.firstOrNull { it.isFavorites }?.entries?.map { it.ref }?.toSet().orEmpty()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event is ManageEvent.NameTaken) {
                Toast.makeText(context, context.getString(R.string.name_taken, event.name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (playlist == null) {
        TvEmptyState(EmptyStateText(R.string.manage_title, R.string.manage_empty))
        return
    }

    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    var groupMenu by remember { mutableStateOf<ChannelGroup?>(null) }
    var renamingGroup by remember { mutableStateOf<ChannelGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<ChannelGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }

    // Move mode keeps a local copy of the list so the item moves instantly; it's saved on OK.
    var movingChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var movingChannelKey by remember { mutableStateOf<String?>(null) }
    var movingGroups by remember { mutableStateOf<List<ChannelGroup>?>(null) }
    var movingGroupKey by remember { mutableStateOf<String?>(null) }
    // The item just placed; once the database has the new order, scroll so it's on screen.
    var placedChannelKey by remember { mutableStateOf<String?>(null) }
    var placedGroupKey by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = movingChannelKey != null) { movingChannelKey = null; movingChannels = null }
    BackHandler(enabled = movingGroupKey != null) { movingGroupKey = null; movingGroups = null }

    val currentGroupKey = selectedGroup ?: groups.firstOrNull()?.key
    val currentGroup = groups.firstOrNull { it.key == currentGroupKey }
    val shownGroups = movingGroups ?: groups
    val shownChannels = movingChannels ?: channels
    val groupsState = rememberLazyListState()
    val channelsState = remember(currentGroupKey) { LazyListState() }
    val firstGroupFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstGroupFocus.requestFocusWhenReady() }

    // After saving, keep showing the new order until the database catches up (no flicker back).
    // A lazy list keeps its scroll anchored to the top row, so a row placed above it would end up
    // off screen: scroll to it.
    LaunchedEffect(channels) {
        if (movingChannelKey != null) return@LaunchedEffect
        movingChannels = null
        val placed = placedChannelKey ?: return@LaunchedEffect
        placedChannelKey = null
        val index = channels.indexOfFirst { it.stableKey == placed }
        if (index >= 0) channelsState.scrollToItem((index - 3).coerceAtLeast(0))
    }
    LaunchedEffect(groups) {
        if (movingGroupKey != null) return@LaunchedEffect
        movingGroups = null
        val placed = placedGroupKey ?: return@LaunchedEffect
        placedGroupKey = null
        val index = groups.indexOfFirst { it.key == placed }
        if (index >= 0) groupsState.scrollToItem((index - 3).coerceAtLeast(0))
    }

    fun keepVisible(state: LazyListState, index: Int) {
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        if (index <= visible.first().index || index >= visible.last().index) {
            scope.launch { state.scrollToItem((index - 3).coerceAtLeast(0)) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.manage_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (movingChannelKey != null || movingGroupKey != null) {
                Text(
                    stringResource(R.string.move_hint_tv),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                OutlinedButton(onClick = { creatingGroup = true }) { Text(stringResource(R.string.new_group)) }
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                Text(
                    stringResource(R.string.manage_groups),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                LazyColumn(
                    state = groupsState,
                    modifier = Modifier.focusRestorer { firstGroupFocus },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(shownGroups, key = { _, it -> it.key }) { index, group ->
                        val moving = group.key == movingGroupKey
                        ManageGroupRow(
                            group = group,
                            selected = group.key == currentGroupKey,
                            moving = moving,
                            onFocused = { if (movingGroupKey == null) vm.selectGroup(group.key) },
                            onClick = {
                                if (moving) {
                                    movingGroupKey = null
                                    placedGroupKey = group.key
                                    vm.moveGroup(shownGroups, group)
                                } else {
                                    groupMenu = group
                                }
                            },
                            onMove = { delta ->
                                val list = movingGroups ?: return@ManageGroupRow
                                val to = index + delta
                                if (to in list.indices) {
                                    movingGroups = list.moved(index, to)
                                    keepVisible(groupsState, to)
                                }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstGroupFocus) else Modifier,
                        )
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (channels.isNotEmpty()) TvHealthBar(vm.healthCheck, channels, Modifier.padding(start = 12.dp, bottom = 8.dp))
                currentGroup?.let { group ->
                    Text(
                        "${group.displayName} · " + stringResource(
                            R.string.group_counts,
                            formatCount(group.visibleCount),
                            formatCount(group.totalCount - group.visibleCount),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                    )
                }
                LazyColumn(
                    state = channelsState,
                    modifier = Modifier.focusRestorer { firstChannelFocus },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(shownChannels, key = { _, it -> it.stableKey }) { index, channel ->
                        val moving = channel.stableKey == movingChannelKey
                        ManageChannelRow(
                            channel = channel,
                            moving = moving,
                            isFavorite = channel.ref in favorites,
                            health = health[channel.ref],
                            onClick = {
                                if (moving) {
                                    movingChannelKey = null
                                    placedChannelKey = channel.stableKey
                                    vm.moveChannel(shownChannels, channel)
                                } else {
                                    editingChannel = channel
                                }
                            },
                            onMove = { delta ->
                                val list = movingChannels ?: return@ManageChannelRow
                                val to = index + delta
                                if (to in list.indices) {
                                    movingChannels = list.moved(index, to)
                                    keepVisible(channelsState, to)
                                }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }

    editingChannel?.let { channel ->
        TvChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { editingChannel = null },
            onNameTaken = vm::nameTaken,
            onChangePosition = {
                movingChannels = channels
                movingChannelKey = channel.stableKey
            },
        )
    }

    groupMenu?.let { group ->
        val close = { groupMenu = null }
        TvMenuDialog(
            title = group.displayName,
            message = if (group.displayName != group.key) stringResource(R.string.originally, group.key) else null,
            onDismiss = close,
            items = buildList {
                add(TvMenuItem(stringResource(R.string.change_position)) {
                    close()
                    movingGroups = groups
                    movingGroupKey = group.key
                })
                add(TvMenuItem(stringResource(R.string.channel_rename)) { close(); renamingGroup = group })
                add(
                    TvMenuItem(stringResource(if (group.hidden) R.string.group_show else R.string.group_hide)) {
                        close()
                        vm.setGroupHidden(group, !group.hidden)
                    },
                )
                add(TvMenuItem(stringResource(R.string.group_sort_az)) { close(); vm.sortGroupAlphabetically(group) })
                add(TvMenuItem(stringResource(R.string.group_reset_order)) { close(); vm.resetGroupOrder(group) })
                if (group.isCustom) add(TvMenuItem(stringResource(R.string.group_delete)) { close(); deletingGroup = group })
            },
        )
    }

    renamingGroup?.let { group ->
        TvTextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_name),
            hint = if (group.isCustom) null else stringResource(R.string.rename_hint),
            initial = group.displayName,
            onDismiss = { renamingGroup = null },
            onSave = { vm.renameGroup(group, it) },
        )
    }

    if (creatingGroup) {
        TvTextEntryDialog(
            title = stringResource(R.string.new_group),
            label = stringResource(R.string.field_group_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = { creatingGroup = false },
            onSave = { if (it.isNotBlank()) vm.createGroup(it) },
        )
    }

    deletingGroup?.let { group ->
        val cancelFocus = remember { FocusRequester() }
        TvDialog(
            title = stringResource(R.string.group_delete_confirm_title, group.displayName),
            message = stringResource(R.string.group_delete_confirm_message),
            onDismiss = { deletingGroup = null },
        ) {
            androidx.tv.material3.Button(
                onClick = { deletingGroup = null },
                modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus),
            ) { Text(stringResource(R.string.cancel)) }
            OutlinedButton(
                onClick = { vm.deleteGroup(group); deletingGroup = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.group_delete)) }
        }
        LaunchedEffect(group.key) { cancelFocus.requestFocusWhenReady() }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

@Composable
private fun ManageGroupRow(
    group: ChannelGroup,
    selected: Boolean,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = buildList {
        if (group.hidden) add(stringResource(R.string.label_hidden))
        if (group.isCustom) add(stringResource(R.string.label_your_group))
        add(
            stringResource(
                R.string.group_counts,
                formatCount(group.visibleCount),
                formatCount(group.totalCount - group.visibleCount),
            ),
        )
    }.joinToString(" · ")
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .moveKeys(moving, onMove)
            .alpha(if (group.hidden && !moving) 0.55f else 1f),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        border = if (moving) movingBorder() else ListItemDefaults.border(),
        headlineContent = { Text(group.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun ManageChannelRow(
    channel: Channel,
    moving: Boolean,
    isFavorite: Boolean,
    health: HealthStatus?,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier
            .moveKeys(moving, onMove)
            .alpha(if (channel.hidden && !moving) 0.55f else 1f),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        border = if (moving) movingBorder() else ListItemDefaults.border(),
        leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 40.dp) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                HealthDot(health, Modifier.padding(start = 10.dp))
            }
        },
        supportingContent = when {
            channel.hidden -> { { Text(stringResource(R.string.label_hidden)) } }
            channel.name != channel.originalName -> { { Text(stringResource(R.string.originally, channel.originalName), maxLines = 1) } }
            else -> null
        },
        trailingContent = if (isFavorite) {
            { Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.label_favorite), modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}
