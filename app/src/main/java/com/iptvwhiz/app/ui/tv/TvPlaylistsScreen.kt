package com.iptvwhiz.app.ui.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.ui.common.importErrorMessage
import com.iptvwhiz.app.ui.common.playlistSummary
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.PlaylistEvent
import com.iptvwhiz.app.ui.state.PlaylistsViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea

/** Every action is a button in a dialog, so nothing here needs long-press or touch. */
@Composable
fun TvPlaylistsScreen(onAddPlaylist: () -> Unit) {
    val vm = appViewModel { container, _ -> PlaylistsViewModel(container) }
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    var actionsFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocusWhenReady()
        vm.events.collect { event ->
            val text = when (event) {
                is PlaylistEvent.Refreshed -> context.getString(R.string.playlist_refreshed, event.name)
                is PlaylistEvent.RefreshFailed -> context.getString(
                    R.string.playlist_refresh_failed, event.name, context.importErrorMessage(event.error),
                )
                is PlaylistEvent.Deleted -> context.getString(R.string.playlist_deleted, event.name)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
    ) {
        Text(stringResource(R.string.playlists_title), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(playlists.orEmpty(), key = { _, it -> it.id }) { index, playlist ->
                ListItem(
                    selected = playlist.id == activeId,
                    onClick = { actionsFor = playlist },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (playlist.id in refreshing) stringResource(R.string.playlist_refreshing)
                            else context.playlistSummary(playlist),
                        )
                    },
                    trailingContent = {
                        when {
                            playlist.id in refreshing -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 3.dp,
                            )
                            playlist.id == activeId -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Text(stringResource(R.string.playlist_active), Modifier.padding(start = 6.dp))
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onAddPlaylist,
                    modifier = if (playlists.isNullOrEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.add_playlist_title)) },
                )
            }
        }
    }

    actionsFor?.let { playlist ->
        val firstAction = remember { FocusRequester() }
        TvDialog(
            title = playlist.name,
            message = context.playlistSummary(playlist),
            onDismiss = { actionsFor = null },
        ) {
            if (playlist.id != activeId) {
                Button(
                    onClick = { vm.setActive(playlist); actionsFor = null },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
                ) { Text(stringResource(R.string.playlist_use)) }
            }
            OutlinedButton(
                onClick = { vm.refresh(playlist); actionsFor = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (playlist.id == activeId) Modifier.focusRequester(firstAction) else Modifier),
            ) { Text(stringResource(R.string.playlist_refresh)) }
            OutlinedButton(
                onClick = { confirmDelete = playlist; actionsFor = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.playlist_delete)) }
        }
        LaunchedEffect(playlist.id) { firstAction.requestFocusWhenReady() }
    }

    confirmDelete?.let { playlist ->
        val cancelFocus = remember { FocusRequester() }
        TvDialog(
            title = stringResource(R.string.playlist_delete_confirm_title, playlist.name),
            message = stringResource(R.string.playlist_delete_confirm_message),
            onDismiss = { confirmDelete = null },
        ) {
            // Cancel first and focused: deleting should take a deliberate move.
            Button(
                onClick = { confirmDelete = null },
                modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus),
            ) { Text(stringResource(R.string.cancel)) }
            OutlinedButton(
                onClick = { vm.delete(playlist); confirmDelete = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.playlist_delete)) }
        }
        LaunchedEffect(playlist.id) { cancelFocus.requestFocusWhenReady() }
    }
}
