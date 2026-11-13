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

/**
 * Like [requestFocusWhenReady], but keeps asking until [hasFocus] says it worked. A request made
 * while a panel is still sliding in, or before an overlay is laid out, can be ignored without an
 * error; this covers that (about a second at most).
 */
suspend fun FocusRequester.requestFocusUntil(hasFocus: () -> Boolean, maxFrames: Int = 60) {
    repeat(maxFrames) {
        if (hasFocus()) return
        withFrameNanos { }
        runCatching { requestFocus() }
    }
}
