package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
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
import com.iptvwhiz.app.data.OrganizedPlaylist
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
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMoveToGroup: () -> Unit,
    onToggleHidden: () -> Unit,
    onChangePosition: (() -> Unit)? = null,
) {
    val items = buildList {
        add(
            TvMenuItem(
                stringResource(if (channel.favorite) R.string.channel_remove_favorite else R.string.channel_add_favorite),
                onToggleFavorite,
            ),
        )
        if (onChangePosition != null) add(TvMenuItem(stringResource(R.string.change_position), onChangePosition))
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

private enum class EditStage { Menu, Rename, Group }

/**
 * The whole "edit this channel" flow for the remote: menu → rename / move to group.
 * [onDone] closes it. [onChangePosition] adds "Change position" (manage screen only).
 */
@Composable
fun TvChannelEditor(
    channel: Channel,
    organized: OrganizedPlaylist?,
    edits: ChannelEdits,
    onDone: () -> Unit,
    onNameTaken: (String) -> Unit,
    onChangePosition: (() -> Unit)? = null,
) {
    var stage by remember(channel.stableKey) { mutableStateOf(EditStage.Menu) }
    when (stage) {
        EditStage.Menu -> TvChannelMenu(
            channel = channel,
            onDismiss = onDone,
            onToggleFavorite = { edits.toggleFavorite(channel); onDone() },
            onRename = { stage = EditStage.Rename },
            onMoveToGroup = { stage = EditStage.Group },
            onToggleHidden = { edits.setHidden(channel, !channel.hidden); onDone() },
            onChangePosition = onChangePosition?.let { change -> { onDone(); change() } },
        )
        EditStage.Rename -> TvTextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_name),
            hint = stringResource(R.string.rename_hint),
            initial = channel.name,
            onDismiss = onDone,
            onSave = { edits.rename(channel, it) },
        )
        EditStage.Group -> if (organized != null) {
            TvGroupPickerDialog(
                groups = organized.groups,
                currentKey = channel.groupTitle,
                onDismiss = onDone,
                onPick = { edits.moveToGroup(channel, it, organized) },
                onNewGroup = { name -> edits.moveToNewGroup(channel, name, organized) { onNameTaken(name) } },
            )
        }
    }
}
