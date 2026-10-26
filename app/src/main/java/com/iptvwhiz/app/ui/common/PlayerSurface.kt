package com.iptvwhiz.app.ui.common

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptvwhiz.app.R

/**
 * The video itself. Controls are drawn in Compose, so PlayerView's own controller is off.
 *
 * [textureView]: draw into a TextureView instead of a SurfaceView. Some phones (seen on a Xiaomi
 * Redmi) show only black for a SurfaceView inside Compose while the sound plays; a TextureView is
 * drawn like any other view. TV keeps the SurfaceView, which is lighter for big streams.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier, textureView: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = if (textureView) {
                LayoutInflater.from(context).inflate(R.layout.player_view_texture, null, false) as PlayerView
            } else {
                PlayerView(context)
            }
            view.apply {
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
