package com.iptvwhiz.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.db.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Why a channel can't play, for a plain-language message. */
enum class PlaybackProblem { Offline, Blocked, NoConnection, Unsupported, Other }

data class LivePlayerState(
    val buffering: Boolean = false,
    val problem: PlaybackProblem? = null,
    /** Technical detail shown under the friendly message, e.g. "HTTP 403". */
    val detail: String? = null,
)

/**
 * ExoPlayer set up for live IPTV:
 * - per-channel user agent / referrer through OkHttp (also follows http→https redirects),
 * - retries network drops with backoff,
 * - jumps back to the live edge when it falls behind,
 * - retries as HLS when a link without ".m3u8" turns out to be HLS.
 */
@OptIn(UnstableApi::class)
class LivePlayer(context: Context, http: OkHttpClient) {

    // Shorter timeouts than playlist downloads: a dead channel should say so in seconds, not a minute.
    private val streamHttp = http.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // No wake mode: the video view keeps the screen on, and playback stops when the app is hidden.
        .build()

    private val _state = MutableStateFlow(LivePlayerState())
    val state: StateFlow<LivePlayerState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var channel: Channel? = null
    private var forceHls = false
    private var retries = 0
    private var stopped = false
    /** A channel that has played and then dropped is worth more retries than one that never started. */
    private var hasPlayed = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                if (playbackState == Player.STATE_READY) {
                    retries = 0
                    hasPlayed = true
                    _state.update { it.copy(problem = null, detail = null) }
                }
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    fun play(channel: Channel) {
        handler.removeCallbacksAndMessages(null)
        this.channel = channel
        forceHls = channel.streamUrl.contains(".m3u8", ignoreCase = true)
        retries = 0
        hasPlayed = false
        _state.value = LivePlayerState(buffering = true)
        load()
    }

    fun retry() {
        handler.removeCallbacksAndMessages(null)
        retries = 0
        _state.value = LivePlayerState(buffering = true)
        load()
    }

    /** Leaving the screen or the app: stop downloading, keep the channel for [resume]. */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        stopped = true
    }

    /** Live TV resumes at the live edge, not where it was paused. No-op unless [stop] was called. */
    fun resume() {
        if (stopped && channel != null) {
            stopped = false
            retry()
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    private fun load() {
        val channel = channel ?: return
        val dataSourceFactory = OkHttpDataSource.Factory(streamHttp)
            .setUserAgent(channel.userAgent ?: AppContainer.USER_AGENT)
            .apply {
                channel.referrer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) }
            }
        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .apply { if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val source = try {
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
        } catch (e: IllegalStateException) {
            // A stream type with no player module in the app (e.g. RTSP, Smooth Streaming).
            _state.value = LivePlayerState(problem = PlaybackProblem.Unsupported)
            return
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            !forceHls && error.errorCode in FORMAT_ERRORS -> {
                // Many IPTV links hide HLS behind URLs without ".m3u8".
                forceHls = true
                load()
            }
            error.isNetworkError() && retries < (if (hasPlayed) MAX_RETRIES else 1) -> {
                val delay = 1_000L shl retries
                retries++
                _state.update { it.copy(buffering = true) }
                handler.postDelayed({ load() }, delay)
            }
            else -> _state.value = LivePlayerState(problem = error.toProblem(), detail = error.detail())
        }
    }

    private fun PlaybackException.isNetworkError(): Boolean = errorCode in NETWORK_ERRORS

    private fun PlaybackException.httpStatus(): Int? =
        (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

    private fun PlaybackException.toProblem(): PlaybackProblem = when {
        httpStatus() == 404 || httpStatus() == 410 -> PlaybackProblem.Offline
        httpStatus() in 400..499 -> PlaybackProblem.Blocked
        errorCode in NETWORK_ERRORS -> PlaybackProblem.NoConnection
        errorCode in FORMAT_ERRORS || errorCode in DECODER_ERRORS -> PlaybackProblem.Unsupported
        else -> PlaybackProblem.Other
    }

    /** Small print under the friendly message: "HTTP 403", or "io network connection failed". */
    private fun PlaybackException.detail(): String =
        httpStatus()?.let { "HTTP $it" }
            ?: errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase()

    private companion object {
        const val MAX_RETRIES = 3

        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )

        val FORMAT_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )

        val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
    }
}
