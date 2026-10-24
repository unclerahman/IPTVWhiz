package com.iptvwhiz.app.ui.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.PlaylistSource
import com.iptvwhiz.app.data.xtream.XtreamCredentials
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.importErrorMessage
import com.iptvwhiz.app.ui.common.isValidUrl
import com.iptvwhiz.app.ui.common.rememberPlaylistFilePicker
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.AddPlaylistViewModel
import com.iptvwhiz.app.ui.state.ImportState
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea

@Composable
fun TvAddM3uScreen(onDone: () -> Unit) {
    val vm = appViewModel { container, _ -> AddPlaylistViewModel(container) }
    val required = stringResource(R.string.field_required)
    val invalid = stringResource(R.string.field_invalid_url)

    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }

    val pickFile = rememberPlaylistFilePicker { uri ->
        vm.submit(name, PlaylistSource.M3uFile(uri, epg.trim().ifEmpty { null }))
    }
    fun submit() {
        urlError = when {
            url.isBlank() -> required
            !isValidUrl(url) -> invalid
            else -> null
        }
        if (urlError == null) vm.submit(name, PlaylistSource.M3uUrl(url.trim(), epg.trim().ifEmpty { null }))
    }

    TvAddPlaylistScaffold(
        title = stringResource(R.string.add_m3u_title),
        vm = vm,
        onDone = onDone,
        onRetry = ::submit,
        firstField = firstField,
    ) {
        TvTextField(
            label = stringResource(R.string.field_m3u_url),
            value = url,
            onValueChange = { url = it; urlError = null },
            placeholder = stringResource(R.string.field_m3u_url_hint),
            error = urlError,
            focusRequester = firstField,
        )
        TvTextField(
            label = stringResource(R.string.field_playlist_name),
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.field_playlist_name_hint),
            keyboardType = KeyboardType.Text,
        )
        TvTextField(
            label = stringResource(R.string.field_epg_url),
            value = epg,
            onValueChange = { epg = it },
            placeholder = stringResource(R.string.field_epg_url_hint),
        )
        // Stacked, not side by side: ▼ from the last field must land on the main action.
        Button(onClick = ::submit) { Text(stringResource(R.string.add_playlist_button)) }
        OutlinedButton(onClick = pickFile) { Text(stringResource(R.string.choose_file)) }
    }
}

@Composable
fun TvAddXtreamScreen(onDone: () -> Unit) {
    val vm = appViewModel { container, _ -> AddPlaylistViewModel(container) }
    val required = stringResource(R.string.field_required)

    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val firstField = remember { FocusRequester() }

    fun submit() {
        showErrors = true
        if (server.isBlank() || username.isBlank() || password.isBlank()) return
        vm.submit(name, PlaylistSource.Xtream(XtreamCredentials(server.trim(), username.trim(), password)))
    }

    TvAddPlaylistScaffold(
        title = stringResource(R.string.add_xtream_title),
        vm = vm,
        onDone = onDone,
        onRetry = ::submit,
        firstField = firstField,
    ) {
        TvTextField(
            label = stringResource(R.string.field_server),
            value = server,
            onValueChange = { server = it },
            placeholder = stringResource(R.string.field_server_hint),
            error = required.takeIf { showErrors && server.isBlank() },
            focusRequester = firstField,
        )
        TvTextField(
            label = stringResource(R.string.field_username),
            value = username,
            onValueChange = { username = it },
            keyboardType = KeyboardType.Text,
            error = required.takeIf { showErrors && username.isBlank() },
        )
        TvTextField(
            label = stringResource(R.string.field_password),
            value = password,
            onValueChange = { password = it },
            isPassword = true,
            error = required.takeIf { showErrors && password.isBlank() },
        )
        TvTextField(
            label = stringResource(R.string.field_playlist_name),
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.field_playlist_name_hint),
            keyboardType = KeyboardType.Text,
        )
        Button(onClick = ::submit) { Text(stringResource(R.string.add_playlist_button)) }
    }
}

/** Title + scrolling form, with the loading panel and error dialog shared by both forms. */
@Composable
private fun TvAddPlaylistScaffold(
    title: String,
    vm: AddPlaylistViewModel,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    firstField: FocusRequester,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { firstField.requestFocusWhenReady() }
    LaunchedEffect(state) {
        val done = state as? ImportState.Done ?: return@LaunchedEffect
        Toast.makeText(
            context, context.getString(R.string.import_done, formatCount(done.channelCount)), Toast.LENGTH_SHORT,
        ).show()
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
    ) {
        when (val current = state) {
            is ImportState.Running, is ImportState.Done -> TvImportProgress(
                found = (current as? ImportState.Running)?.channelsFound ?: 0,
                onCancel = vm::cancel,
            )
            else -> Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(760.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                fields()
            }
        }

        val failed = state as? ImportState.Failed
        if (failed != null) {
            val retryFocus = remember { FocusRequester() }
            TvDialog(
                title = stringResource(R.string.import_failed_title),
                message = context.importErrorMessage(failed.error),
                onDismiss = vm::dismissError,
            ) {
                Button(
                    onClick = { vm.dismissError(); onRetry() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(retryFocus),
                ) { Text(stringResource(R.string.try_again)) }
                OutlinedButton(onClick = vm::dismissError, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back))
                }
            }
            LaunchedEffect(Unit) { retryFocus.requestFocusWhenReady() }
        }
    }
}

@Composable
private fun TvImportProgress(found: Int, onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocusWhenReady() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.import_running), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (found > 0) stringResource(R.string.import_found, formatCount(found)) else " ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
