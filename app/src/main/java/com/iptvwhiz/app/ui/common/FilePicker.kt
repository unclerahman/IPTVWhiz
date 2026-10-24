package com.iptvwhiz.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.iptvwhiz.app.R

/**
 * Returns a function that opens the system file picker for a playlist file.
 * Keeps read access so the playlist can be refreshed later. Many TV boxes ship without
 * a file picker; that case shows a message instead of crashing.
 */
@Composable
fun rememberPlaylistFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(uri)
        }
    }
    return {
        try {
            if (!context.hasDocumentPicker()) throw ActivityNotFoundException()
            // M3U files arrive with many different (or no) MIME types, so allow any file.
            launcher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_file_picker, Toast.LENGTH_LONG).show()
        }
    }
}
