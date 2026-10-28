package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.PlaylistEntity
import com.iptvwhiz.app.ui.common.importErrorMessage
import com.iptvwhiz.app.ui.common.playlistSummary
import com.iptvwhiz.app.ui.state.PlaylistEvent
import com.iptvwhiz.app.ui.state.PlaylistsViewModel
import com.iptvwhiz.app.ui.state.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilePlaylistsScreen(onAddPlaylist: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> PlaylistsViewModel(container) }
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    LaunchedEffect(Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlaylist,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_playlist_title)) },
            )
        },
    ) { padding ->
        if (playlists?.isEmpty() == true) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.playlists_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(playlists.orEmpty(), key = { it.id }) { playlist ->
                var menuOpen by remember { mutableStateOf(false) }
                val isActive = playlist.id == activeId
                ListItem(
                    leadingContent = {
                        // Reserve the slot even when this playlist is not active, so every title
                        // starts at the same x.
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (isActive) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(R.string.playlist_active),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (playlist.id in refreshing) stringResource(R.string.playlist_refreshing)
                            else context.playlistSummary(playlist),
                        )
                    },
                    trailingContent = {
                        if (playlist.id in refreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        } else {
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (!isActive) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.playlist_use)) },
                                            onClick = { vm.setActive(playlist); menuOpen = false },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.playlist_refresh)) },
                                        onClick = { vm.refresh(playlist); menuOpen = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.playlist_delete)) },
                                        onClick = { confirmDelete = playlist; menuOpen = false },
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { vm.setActive(playlist) },
                )
                HorizontalDivider()
            }
        }
    }

    confirmDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.playlist_delete_confirm_title, playlist.name)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(playlist); confirmDelete = null }) {
                    Text(stringResource(R.string.playlist_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
