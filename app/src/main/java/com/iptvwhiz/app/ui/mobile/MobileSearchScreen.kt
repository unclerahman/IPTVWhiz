package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelSearch
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.countryName
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.state.HealthCheck
import com.iptvwhiz.app.ui.state.HealthCheckState
import com.iptvwhiz.app.ui.state.SearchViewModel
import com.iptvwhiz.app.ui.state.appViewModel

/** Search across all playlists: name box, then country / group / playlist chips. */
@Composable
fun MobileSearchContent(onPlay: (Channel) -> Unit, modifier: Modifier) {
    val vm = appViewModel { container, _ -> SearchViewModel(container) }
    val context = LocalContext.current
    val filters by vm.filters.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Channel?>(null) }
    val names = playlists.associate { it.id to it.name }

    Column(modifier) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = vm::setQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (filters.query.isNotEmpty()) {
                { IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.filter_clear)) } }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipMenu(
                label = filters.country?.let { countryName(it) } ?: stringResource(R.string.filter_any_country),
                selected = filters.country != null,
                anyLabel = stringResource(R.string.filter_any_country),
                options = countries.map { (code, count) -> code to "${countryName(code)} · ${formatCount(count)}" },
                onPick = vm::setCountry,
            )
            ChipMenu(
                label = filters.group ?: stringResource(R.string.filter_any_group),
                selected = filters.group != null,
                anyLabel = stringResource(R.string.filter_any_group),
                options = groups.map { (name, count) -> name to "$name · ${formatCount(count)}" },
                onPick = vm::setGroup,
            )
            if (playlists.size > 1) {
                ChipMenu(
                    label = filters.playlistId?.let { names[it] } ?: stringResource(R.string.filter_all_playlists),
                    selected = filters.playlistId != null,
                    anyLabel = stringResource(R.string.filter_all_playlists),
                    options = playlists.map { it.id.toString() to it.name },
                    onPick = { vm.setPlaylist(it?.toLong()) },
                )
            }
        }
        Text(
            when {
                filters.isEmpty -> stringResource(R.string.search_prompt)
                results.isEmpty() -> stringResource(R.string.search_no_results)
                results.size >= ChannelSearch.MAX_RESULTS -> stringResource(R.string.search_capped, formatCount(results.size))
                else -> pluralStringResource(R.plurals.search_count, results.size, formatCount(results.size))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (results.isNotEmpty()) HealthBar(vm.healthCheck, results, Modifier.padding(horizontal = 16.dp))
        MobileChannelList(
            channels = results,
            guide = guide,
            health = health,
            favorites = favorites,
            subtitle = { channel ->
                if (names.size > 1) "${names[channel.playlistId].orEmpty()} · ${channel.groupTitle}" else channel.groupTitle
            },
            listState = rememberLazyListState(),
            onPlay = { channel ->
                vm.useResultsAsQueue()
                onPlay(channel)
            },
            onLongPress = { editing = it },
        )
    }
    editing?.let { channel ->
        ChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name -> Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show() },
        )
    }
}

/** A filter chip that opens a menu: "Any" plus [options] (key to label). */
@Composable
private fun ChipMenu(
    label: String,
    selected: Boolean,
    anyLabel: String,
    options: List<Pair<String, String>>,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(anyLabel) }, onClick = { open = false; onPick(null) })
            options.take(200).forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(key) })
            }
        }
    }
}

/** "Check channels" → progress → summary with "Hide N offline". */
@Composable
internal fun HealthBar(check: HealthCheck, channels: List<Channel>, modifier: Modifier = Modifier) {
    val state by check.state.collectAsStateWithLifecycle()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val current = state) {
            HealthCheckState.Idle -> OutlinedButton(onClick = { check.start(channels) }) { Text(stringResource(R.string.health_check)) }
            is HealthCheckState.Running -> {
                Text(stringResource(R.string.health_checking, current.done, current.total), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = check::stop) { Text(stringResource(R.string.health_stop)) }
            }
            is HealthCheckState.Done -> {
                Text(
                    stringResource(R.string.health_summary, current.summary.working, current.summary.offline, current.summary.blocked),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (current.offline.isNotEmpty()) {
                    Button(onClick = { check.hide(includeBlocked = false) }) {
                        Text(stringResource(R.string.health_hide_offline, current.offline.size))
                    }
                }
                if (current.blocked.isNotEmpty()) {
                    OutlinedButton(onClick = { check.hide(includeBlocked = true) }) {
                        Text(stringResource(R.string.health_hide_all, current.offline.size + current.blocked.size))
                    }
                }
                TextButton(onClick = check::dismiss) { Text(stringResource(R.string.health_dismiss)) }
            }
        }
    }
}
