package com.iptvwhiz.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.R
import com.iptvwhiz.app.ui.state.UpdateState
import com.iptvwhiz.app.ui.state.UpdateViewModel

/** Repo the releases are published to; also the browser fallback when a release has no APK. */
private const val RELEASES_URL = "https://github.com/unclerahman/IPTVWhiz/releases/latest"

/**
 * The update screen, shared by the TV and phone layouts: shows what the newest GitHub release is,
 * and hands the APK to Android's installer. Text and buttons are plain; the owner is non-technical.
 *
 * TV has no mobile MaterialTheme, so its screen passes the brand [accent] pair for buttons; the
 * phone passes nothing and gets the app theme's own colors. [focusButtons] keeps the remote
 * pointed at the one active button after every state change.
 */
@Composable
fun UpdateBody(
    vm: UpdateViewModel,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onAccent: Color? = null,
    /** Text color; TV must pass one because it has no mobile theme to supply light text. */
    textColor: Color? = null,
    focusButtons: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val text = textColor ?: MaterialTheme.colorScheme.onSurface

    LaunchedEffect(Unit) {
        vm.checkQuietly()
    }

    if (focusButtons) {
        // The active button changes with the state; keep it in reach of the D-pad.
        LaunchedEffect(state) { focus.requestFocusWhenReady() }
    }

    // The APK is downloaded: ask the system to install it, once — the AwaitingInstall state
    // consumes this so coming back from the installer doesn't launch it again.
    LaunchedEffect(state) {
        val installing = state as? UpdateState.Installing ?: return@LaunchedEffect
        context.startActivity(vm.installIntent(context, installing.apkUri))
        vm.installRequested()
    }

    val filled = if (accent == null) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent ?: Color.White)
    }
    val outlined = if (accent == null) {
        ButtonDefaults.outlinedButtonColors()
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = accent)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = accent ?: MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.update_checking), color = text)
            }

            is UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME), color = text)

            is UpdateState.Available -> {
                Text(
                    stringResource(R.string.update_available, s.version),
                    style = MaterialTheme.typography.titleLarge,
                color = text,
                )
                if (!s.notes.isNullOrBlank()) {
                    Text(stringResource(R.string.update_notes_title), style = MaterialTheme.typography.titleSmall, color = text)
                    // Release notes are plain markdown text; real line breaks are kept as-is.
                    Text(s.notes.replace("**", ""), style = MaterialTheme.typography.bodyMedium, color = text)
                }
                if (s.apkUrl != null) {
                    Button(
                        onClick = { vm.downloadAndInstall(context) },
                        colors = filled,
                        modifier = if (focusButtons) Modifier.focusRequester(focus) else Modifier,
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                } else {
                    Text(stringResource(R.string.update_no_apk), color = text)
                    OutlinedButton(
                        onClick = { openReleasePage(context) },
                        colors = outlined,
                        modifier = if (focusButtons) Modifier.focusRequester(focus) else Modifier,
                    ) {
                        Text(stringResource(R.string.update_open_browser))
                    }
                }
            }

            is UpdateState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = accent ?: MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.update_downloading, s.percent), color = text)
            }

            is UpdateState.Installing, is UpdateState.AwaitingInstall -> {
                Text(stringResource(R.string.update_installing), color = text)
                // A canceled install can be retried by checking again.
                OutlinedButton(
                    onClick = { vm.check() },
                    colors = outlined,
                    modifier = if (focusButtons) Modifier.focusRequester(focus) else Modifier,
                ) {
                    Text(stringResource(R.string.settings_update))
                }
            }

            is UpdateState.Failed -> {
                Text(
                    if (s.fromDownload) stringResource(R.string.update_download_failed)
                    else stringResource(R.string.update_error),
                    color = text,
                )
                OutlinedButton(
                    onClick = { vm.check() },
                    colors = outlined,
                    modifier = if (focusButtons) Modifier.focusRequester(focus) else Modifier,
                ) {
                    Text(stringResource(R.string.update_try_again))
                }
            }

            UpdateState.Idle -> OutlinedButton(
                onClick = { vm.check() },
                colors = outlined,
                modifier = if (focusButtons) Modifier.focusRequester(focus) else Modifier,
            ) {
                Text(stringResource(R.string.settings_update))
            }
        }
    }
}

/** Fallback when a release has no APK asset: the repo's release page in the browser. */
private fun openReleasePage(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
    }
}
