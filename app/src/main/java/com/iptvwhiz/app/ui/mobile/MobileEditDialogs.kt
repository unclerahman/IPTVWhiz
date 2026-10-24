package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelGroup
import com.iptvwhiz.app.data.OrganizedPlaylist
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.state.ChannelEdits

data class SheetAction(val label: String, val onClick: () -> Unit)

/** Bottom sheet with a title and a list of actions. Actions close the sheet themselves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = subtitle?.let { { Text(it) } },
            )
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.label) },
                    modifier = Modifier.clickable(onClick = action.onClick),
                )
            }
        }
    }
}

@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    hint: String? = null,
    saveLabel: String = stringResource(R.string.save),
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                supportingText = hint?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave(text) }) { Text(saveLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun GroupPickerDialog(
    groups: List<ChannelGroup>,
    currentKey: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onNewGroup: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TextEntryDialog(
            title = stringResource(R.string.new_group),
            label = stringResource(R.string.field_group_name),
            initial = "",
            saveLabel = stringResource(R.string.create),
            onDismiss = onDismiss,
            onSave = { if (it.isNotBlank()) onNewGroup(it) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_group_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.new_group)) },
                        modifier = Modifier.clickable { naming = true },
                    )
                }
                items(groups, key = { it.key }) { group ->
                    ListItem(
                        headlineContent = { Text(group.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = if (group.key == currentKey) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onDismiss(); onPick(group.key) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private enum class EditStage { Menu, Rename, Group }

/** The "edit this channel" flow for touch: sheet → rename / move to group. */
@Composable
fun ChannelEditor(
    channel: Channel,
    organized: OrganizedPlaylist?,
    edits: ChannelEdits,
    onDone: () -> Unit,
    onNameTaken: (String) -> Unit,
) {
    var stage by remember(channel.stableKey) { mutableStateOf(EditStage.Menu) }
    when (stage) {
        EditStage.Menu -> ActionSheet(
            title = channel.name,
            subtitle = if (channel.name != channel.originalName) stringResource(R.string.originally, channel.originalName) else null,
            onDismiss = onDone,
            actions = listOf(
                SheetAction(
                    stringResource(if (channel.favorite) R.string.channel_remove_favorite else R.string.channel_add_favorite),
                ) { edits.toggleFavorite(channel); onDone() },
                SheetAction(stringResource(R.string.channel_rename)) { stage = EditStage.Rename },
                SheetAction(stringResource(R.string.channel_move_group)) { stage = EditStage.Group },
                SheetAction(stringResource(if (channel.hidden) R.string.channel_show else R.string.channel_hide)) {
                    edits.setHidden(channel, !channel.hidden)
                    onDone()
                },
            ),
        )
        EditStage.Rename -> TextEntryDialog(
            title = stringResource(R.string.channel_rename),
            label = stringResource(R.string.field_name),
            hint = stringResource(R.string.rename_hint),
            initial = channel.name,
            onDismiss = onDone,
            onSave = { edits.rename(channel, it) },
        )
        EditStage.Group -> if (organized != null) {
            GroupPickerDialog(
                groups = organized.groups,
                currentKey = channel.groupTitle,
                onDismiss = onDone,
                onPick = { edits.moveToGroup(channel, it, organized) },
                onNewGroup = { name -> edits.moveToNewGroup(channel, name, organized) { onNameTaken(name) } },
            )
        }
    }
}
