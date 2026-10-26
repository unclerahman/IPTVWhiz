package com.iptvwhiz.app.ui.tv

import android.widget.Toast
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.ChannelSearch
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.common.countryName
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.HealthCheck
import com.iptvwhiz.app.ui.state.HealthCheckState
import com.iptvwhiz.app.ui.state.SearchViewModel
import com.iptvwhiz.app.ui.state.appViewModel

private enum class Picker { Country, Group, Playlist }

/** Search across all playlists: name, then country, group and playlist filters. */
@Composable
fun TvSearchContent(onPlay: (Channel) -> Unit) {
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
    var picker by remember { mutableStateOf<Picker?>(null) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    val fieldFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val names = playlists.associate { it.id to it.name }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            // Down from the field or a filter goes to Clear / Check channels; spatial search skips them.
            Modifier.onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                down && !filters.isEmpty && runCatching { clearFocus.requestFocus() }.isSuccess
            },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvTextField(
                label = stringResource(R.string.search_field),
                value = filters.query,
                onValueChange = vm::setQuery,
                placeholder = stringResource(R.string.search_hint),
                keyboardType = KeyboardType.Text,
                focusRequester = fieldFocus,
                // Shrinks so the filter buttons always fit beside it.
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { picker = Picker.Country }, modifier = Modifier.widthIn(max = 280.dp)) {
                Text(filters.country?.let { countryName(it) } ?: stringResource(R.string.filter_any_country), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = { picker = Picker.Group }, modifier = Modifier.widthIn(max = 280.dp)) {
                Text(filters.group ?: stringResource(R.string.filter_any_group), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (playlists.size > 1) {
                OutlinedButton(onClick = { picker = Picker.Playlist }, modifier = Modifier.widthIn(max = 280.dp)) {
                    Text(filters.playlistId?.let { names[it] } ?: stringResource(R.string.filter_all_playlists), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    filters.isEmpty -> stringResource(R.string.search_prompt)
                    results.isEmpty() -> stringResource(R.string.search_no_results)
                    results.size >= ChannelSearch.MAX_RESULTS -> stringResource(R.string.search_capped, formatCount(results.size))
                    else -> pluralStringResource(R.plurals.search_count, results.size, formatCount(results.size))
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(24.dp))
            if (!filters.isEmpty) {
                OutlinedButton(onClick = vm::clear, modifier = Modifier.focusRequester(clearFocus)) { Text(stringResource(R.string.filter_clear)) }
                Spacer(Modifier.width(12.dp))
            }
            if (results.isNotEmpty()) TvHealthBar(vm.healthCheck, results)
        }
        // The line above already says why there are no results.
        if (results.isNotEmpty()) TvChannelList(
            channels = results,
            guide = guide,
            health = health,
            favorites = favorites,
            subtitle = { channel ->
                if (names.size > 1) "${names[channel.playlistId].orEmpty()} · ${channel.groupTitle}" else channel.groupTitle
            },
            listKey = filters.toString(),
            lastPlayedKey = null,
            onPlay = { channel ->
                vm.useResultsAsQueue()
                onPlay(channel)
            },
            onEdit = { editing = it },
        )
    }
    LaunchedEffect(Unit) { fieldFocus.requestFocusWhenReady() }

    when (picker) {
        Picker.Country -> TvOptionDialog(
            title = stringResource(R.string.filter_country),
            anyLabel = stringResource(R.string.filter_any_country),
            options = countries.map { (code, count) -> code to "${countryName(code)}  ·  ${formatCount(count)}" },
            selected = filters.country,
            onPick = { vm.setCountry(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.Group -> TvOptionDialog(
            title = stringResource(R.string.filter_group),
            anyLabel = stringResource(R.string.filter_any_group),
            options = groups.map { (name, count) -> name to "$name  ·  ${formatCount(count)}" },
            selected = filters.group,
            onPick = { vm.setGroup(it); picker = null },
            onDismiss = { picker = null },
        )
        Picker.Playlist -> TvOptionDialog(
            title = stringResource(R.string.filter_playlist),
            anyLabel = stringResource(R.string.filter_all_playlists),
            options = playlists.map { it.id.toString() to it.name },
            selected = filters.playlistId?.toString(),
            onPick = { vm.setPlaylist(it?.toLong()); picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }

    editing?.let { channel ->
        TvChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { editing = null },
            onNameTaken = { name ->
                Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** "Any" plus a list of options; [onPick] gets the option key, or null for "Any". */
@Composable
private fun TvOptionDialog(
    title: String,
    anyLabel: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Open on the current choice; "Any" is row 0.
    val current = remember { FocusRequester() }
    val selectedRow = options.indexOfFirst { it.first == selected } + 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedRow - 2).coerceAtLeast(0))
    TvDialog(title = title, onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 480.dp), state = listState) {
            item {
                ListItem(
                    selected = selected == null,
                    onClick = { onPick(null) },
                    modifier = if (selectedRow == 0) Modifier.focusRequester(current) else Modifier,
                    headlineContent = { Text(anyLabel) },
                )
            }
            items(options, key = { it.first }) { (key, label) ->
                ListItem(
                    selected = key == selected,
                    onClick = { onPick(key) },
                    modifier = if (key == selected) Modifier.focusRequester(current) else Modifier,
                    headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { current.requestFocusWhenReady() }
}

/** "Check channels" → progress → summary with "Hide N offline". Used by search, manage and watchlists. */
@Composable
fun TvHealthBar(check: HealthCheck, channels: List<Channel>, modifier: Modifier = Modifier) {
    val state by check.state.collectAsStateWithLifecycle()
    // Each state swaps the buttons out; keep focus in the bar rather than losing it to the rail.
    val primary = remember { FocusRequester() }
    var focusedIn by remember { mutableStateOf<Any?>(null) }
    var refocus by remember { mutableStateOf(false) }
    LaunchedEffect(refocus, state::class) {
        if (refocus) {
            primary.requestFocusWhenReady()
            refocus = false
        }
    }
    Row(
        modifier
            .onFocusChanged { focus ->
                val kind = check.state.value::class
                if (focus.hasFocus) {
                    focusedIn = kind
                } else {
                    if (focusedIn != null && focusedIn != kind) refocus = true
                    focusedIn = null
                }
            }
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val current = state) {
            HealthCheckState.Idle -> OutlinedButton(onClick = { check.start(channels) }, modifier = Modifier.focusRequester(primary)) {
                Text(stringResource(R.string.health_check))
            }
            is HealthCheckState.Running -> {
                Text(
                    stringResource(R.string.health_checking, current.done, current.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(onClick = check::stop, modifier = Modifier.focusRequester(primary)) { Text(stringResource(R.string.health_stop)) }
            }
            is HealthCheckState.Done -> {
                Text(
                    stringResource(R.string.health_summary, current.summary.working, current.summary.offline, current.summary.blocked),
                    style = MaterialTheme.typography.titleMedium,
                )
                val hideFirst = current.offline.isNotEmpty()
                if (hideFirst) {
                    Button(onClick = { check.hide(includeBlocked = false) }, modifier = Modifier.focusRequester(primary)) {
                        Text(stringResource(R.string.health_hide_offline, current.offline.size))
                    }
                }
                if (current.blocked.isNotEmpty()) {
                    OutlinedButton(onClick = { check.hide(includeBlocked = true) }) {
                        Text(stringResource(R.string.health_hide_all, current.offline.size + current.blocked.size), maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = check::dismiss,
                    modifier = if (hideFirst) Modifier else Modifier.focusRequester(primary),
                ) { Text(stringResource(R.string.health_dismiss)) }
            }
        }
    }
}
