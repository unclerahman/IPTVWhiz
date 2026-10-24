package com.iptvwhiz.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.iptvwhiz.app.R

private const val EXIT_WINDOW_MS = 2_000L

/** "Press Back again to exit" on the root screen, so a stray Back press doesn't close the app. */
@Composable
fun BackToExit() {
    val context = LocalContext.current
    val lastBack = remember { mutableLongStateOf(0L) }
    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack.longValue < EXIT_WINDOW_MS) {
            context.findActivity()?.finish()
        } else {
            lastBack.longValue = now
            Toast.makeText(context, R.string.press_back_again, Toast.LENGTH_SHORT).show()
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
