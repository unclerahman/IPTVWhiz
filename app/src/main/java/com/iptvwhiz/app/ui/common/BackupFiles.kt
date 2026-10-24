package com.iptvwhiz.app.ui.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A backup file the app can open without a file picker. */
data class BackupCandidate(val uri: Uri, val name: String, val modified: Long)

/**
 * Where backups go on devices without a file picker (most TV boxes): Download/IPTV Whiz.
 * The folder is reachable over USB and by file-manager apps, so a file can be moved between devices.
 */
object BackupFiles {
    const val FOLDER = "IPTV Whiz"

    fun suggestedName(): String =
        "iptvwhiz-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.json"

    /** Creates a new backup file in Download/IPTV Whiz. Returns its stream and a readable location. */
    fun create(context: Context, name: String): Pair<OutputStream, String>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = context.contentResolver.openOutputStream(uri) ?: return null
            return stream to "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
        }
        // Android 7–9: the app's own folder needs no permission and is visible over USB.
        val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val file = File(dir, name)
        return file.outputStream() to file.absolutePath
    }

    /** Backups this app saved earlier (newest first), for restoring without a file picker. */
    fun find(context: Context): List<BackupCandidate> {
        val found = mutableListOf<BackupCandidate>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED,
            )
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                    arrayOf("%.json"),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        found += BackupCandidate(
                            uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()),
                            name = cursor.getString(1),
                            modified = cursor.getLong(2) * 1000,
                        )
                    }
                }
            }
        }
        File(context.getExternalFilesDir(null), "backups").listFiles { f -> f.name.endsWith(".json") }?.forEach {
            found += BackupCandidate(Uri.fromFile(it), it.name, it.lastModified())
        }
        return found.sortedByDescending { it.modified }
    }
}
