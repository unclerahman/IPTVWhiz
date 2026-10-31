package com.iptvwhiz.app.ui.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.data.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** What the update screen is doing right now. */
sealed interface UpdateState {
    /** Nothing asked yet. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** The installed build is the newest release. */
    data object UpToDate : UpdateState

    data class Available(
        val version: String,
        val notes: String?,
        /** Missing when the release has no APK asset; the browser link is offered instead. */
        val apkUrl: String?,
    ) : UpdateState

    /** Percentage of the APK downloaded so far, 0..100. */
    data class Downloading(val percent: Int) : UpdateState

    /** The APK is ready and the installer was just opened. */
    data class Installing(val apkUri: Uri) : UpdateState

    /** The installer was opened; this stays until the user asks again (installs don't report back). */
    data object AwaitingInstall : UpdateState

    /** [fromDownload] distinguishes a failed APK download from a failed version check. */
    data class Failed(val reason: Reason, val fromDownload: Boolean = false) : UpdateState

    enum class Reason { NoConnection, Server, NoSpace, Denied }
}

/**
 * Checks GitHub for a newer release and hands the APK to Android's package installer. The app
 * only downloads from its own repository and never runs an APK itself — installing is the
 * system's job (PLAN.md: sideloaded debug-signed releases).
 */
class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Fetches the newest release and compares it with the installed version. */
    fun check() {
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            val release = fetchRelease()
            _state.value = when {
                release == null -> UpdateState.Failed(UpdateState.Reason.Server)
                UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME) ->
                    UpdateState.Available(release.version, release.notes, release.apkUrl)
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Called once when the screen opens, quietly: a failed check stays silent. */
    fun checkQuietly() {
        if (_state.value != UpdateState.Idle) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            val release = fetchRelease()
            _state.value = when {
                release == null -> UpdateState.Idle
                UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME) ->
                    UpdateState.Available(release.version, release.notes, release.apkUrl)
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Downloads the APK into the cache and asks Android to install it. */
    fun downloadAndInstall(context: Context) {
        val url = (state.value as? UpdateState.Available)?.apkUrl ?: return
        _state.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { downloadApk(url) }
            if (file == null) {
                _state.value = UpdateState.Failed(UpdateState.Reason.Server, fromDownload = true)
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            _state.value = UpdateState.Installing(uri)
        }
    }

    /** The installer intent for a downloaded APK; the system takes it from here. */
    fun installIntent(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Marks the installer as opened so returning to the app doesn't open it again. */
    fun installRequested() {
        if (_state.value is UpdateState.Installing) _state.value = UpdateState.AwaitingInstall
    }

    fun dismissError() {
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchRelease(): com.iptvwhiz.app.data.update.Release? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(UpdateChecker.LATEST_URL).build()
                container.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    UpdateChecker.parseRelease(body)
                }
            }.getOrNull()
        }

    /**
     * Streams the APK to the cache dir with a progress callback. Returns null when the download
     * fails midway or the disk is full; the cache is wiped by the system when space is needed.
     */
    private fun downloadApk(url: String): File? = runCatching {
        val request = Request.Builder().url(url).build()
        container.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val total = body.contentLength().takeIf { it > 0 }
            val out = File(container.appContext.cacheDir, "update.apk")
            out.outputStream().use { sink ->
                val source = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    copied += read
                    total?.let { t ->
                        _state.update { s ->
                            if (s is UpdateState.Downloading) s.copy(percent = (copied * 100 / t).toInt()) else s
                        }
                    }
                }
            }
            out
        }
    }.getOrNull()
}
