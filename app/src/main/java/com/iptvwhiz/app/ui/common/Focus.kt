package com.iptvwhiz.app.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Lazy list items are composed during layout, after effects start, so a plain requestFocus()
 * can run before the target exists. Wait a few frames for it instead of crashing or giving up.
 */
suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 10) {
    repeat(maxFrames) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.isSuccess) return
    }
}
