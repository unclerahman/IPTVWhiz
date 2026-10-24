package com.iptvwhiz.app.ui.common

import android.graphics.Color
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** The video itself. Controls are drawn in Compose, so PlayerView's own controller is off. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                setShutterBackgroundColor(Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                // Never take D-pad focus away from the Compose UI.
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
