package com.iptvwhiz.app.ui.mobile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.PlaylistSource
import com.iptvwhiz.app.data.xtream.XtreamCredentials
import com.iptvwhiz.app.ui.common.formatCount
import com.iptvwhiz.app.ui.common.importErrorMessage
import com.iptvwhiz.app.ui.common.isValidUrl
import com.iptvwhiz.app.ui.common.rememberPlaylistFilePicker
import com.iptvwhiz.app.ui.state.AddPlaylistViewModel
import com.iptvwhiz.app.ui.state.ImportState
import com.iptvwhiz.app.ui.state.appViewModel

@Composable
fun MobileAddM3uScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> AddPlaylistViewModel(container) }
    val required = stringResource(R.string.field_required)
    val invalid = stringResource(R.string.field_invalid_url)

    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }

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

    MobileAddPlaylistScaffold(stringResource(R.string.add_m3u_title), vm, onDone, onBack, onRetry = ::submit) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; urlError = null },
            label = { Text(stringResource(R.string.field_m3u_url)) },
            placeholder = { Text(stringResource(R.string.field_m3u_url_hint)) },
            isError = urlError != null,
            supportingText = urlError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_playlist_name)) },
            supportingText = { Text(stringResource(R.string.field_playlist_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = epg,
            onValueChange = { epg = it },
            label = { Text(stringResource(R.string.field_epg_url)) },
            supportingText = { Text(stringResource(R.string.field_epg_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_playlist_button))
        }
        OutlinedButton(onClick = pickFile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.choose_file))
        }
    }
}

@Composable
fun MobileAddXtreamScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { container, _ -> AddPlaylistViewModel(container) }
    val required = stringResource(R.string.field_required)

    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    fun submit() {
        showErrors = true
        if (server.isBlank() || username.isBlank() || password.isBlank()) return
        vm.submit(name, PlaylistSource.Xtream(XtreamCredentials(server.trim(), username.trim(), password)))
    }

    @Composable
    fun field(
        value: String,
        onChange: (String) -> Unit,
        label: Int,
        mustFill: Boolean,
        keyboardType: KeyboardType = KeyboardType.Text,
        password: Boolean = false,
    ) {
        val error = mustFill && showErrors && value.isBlank()
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            isError = error,
            supportingText = if (error) ({ Text(required) }) else null,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    MobileAddPlaylistScaffold(stringResource(R.string.add_xtream_title), vm, onDone, onBack, onRetry = ::submit) {
        field(server, { server = it }, R.string.field_server, mustFill = true, keyboardType = KeyboardType.Uri)
        field(username, { username = it }, R.string.field_username, mustFill = true)
        field(password, { password = it }, R.string.field_password, mustFill = true, password = true)
        field(name, { name = it }, R.string.field_playlist_name, mustFill = false)
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_playlist_button))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileAddPlaylistScaffold(
    title: String,
    vm: AddPlaylistViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val done = state as? ImportState.Done ?: return@LaunchedEffect
        Toast.makeText(
            context, context.getString(R.string.import_done, formatCount(done.channelCount)), Toast.LENGTH_SHORT,
        ).show()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                is ImportState.Running, is ImportState.Done -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.import_running), style = MaterialTheme.typography.titleMedium)
                    val found = (current as? ImportState.Running)?.channelsFound ?: 0
                    Text(
                        text = if (found > 0) stringResource(R.string.import_found, formatCount(found)) else " ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                }
                else -> Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = fields,
                )
            }
        }
    }

    (state as? ImportState.Failed)?.let { failed ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(context.importErrorMessage(failed.error)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissError(); onRetry() }) { Text(stringResource(R.string.try_again)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.back)) }
            },
        )
    }
}
