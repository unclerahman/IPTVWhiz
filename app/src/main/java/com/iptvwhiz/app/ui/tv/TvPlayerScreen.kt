package com.iptvwhiz.app.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.ref
import com.iptvwhiz.app.ui.common.HealthBadge
import com.iptvwhiz.app.ui.state.LiveViewModel
import com.iptvwhiz.app.ui.theme.TvSafeArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.layout.size
import androidx.tv.material3.Icon
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.nextLine
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.PlayerSurface
import com.iptvwhiz.app.ui.common.QualityBadge
import com.iptvwhiz.app.ui.common.playbackProblemMessage
import com.iptvwhiz.app.ui.common.requestFocusWhenReady
import com.iptvwhiz.app.ui.state.PlayerViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import kotlinx.coroutines.delay

private const val BANNER_MS = 4_000L
private const val NUMBER_ENTRY_MS = 1_500L

/**
 * Full-screen live TV for the remote:
 * ▲/CH+ next channel, ▼/CH− previous, OK or ◀ channel list, ▶ or Info shows the banner,
 * digits jump to a channel number, Back closes the list and then leaves.
 */
@Composable
fun TvPlayerScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> PlayerViewModel(container, savedState) }
    val context = LocalContext.current
    val channels by vm.channels.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playerState by vm.livePlayer.state.collectAsStateWithLifecycle()
    val groupName by vm.groupName.collectAsStateWithLifecycle()
    val programme by vm.programme.collectAsStateWithLifecycle()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    val showQuality by vm.showQuality.collectAsStateWithLifecycle()
    val stats by vm.livePlayer.stats.collectAsStateWithLifecycle()
    val listKey by vm.listKey.collectAsStateWithLifecycle()
    // Browsing while watching: the same lists, guide and status as Live TV.
    val browse = appViewModel { container, _ -> LiveViewModel(container) }
    val guide by browse.guide.collectAsStateWithLifecycle()
    val health by browse.health.collectAsStateWithLifecycle()
    val favorites by browse.favorites.collectAsStateWithLifecycle()
    /** The channel under the cursor in the browse panel, for the preview card. */
    var previewed by remember { mutableStateOf<Channel?>(null) }

    var listVisible by remember { mutableStateOf(false) }
    /** The timeline guide over the video (quick menu → TV guide, or the remote's Guide key). */
    var guideVisible by remember { mutableStateOf(false) }
    /** The channel whose quick menu is open (long-press OK / Menu), if any. */
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    /** Set while OK is held down long enough to count as a long press. */
    var okHeld by remember { mutableStateOf(false) }
    var bannerToken by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    val rootFocus = remember { FocusRequester() }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.livePlayer.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.livePlayer.resume() }

    // Show the banner on every channel change and on request, then hide it.
    LaunchedEffect(current?.stableKey, bannerToken) {
        if (current == null) return@LaunchedEffect
        bannerVisible = true
        delay(BANNER_MS)
        bannerVisible = false
    }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(NUMBER_ENTRY_MS)
        val number = digits.toInt()
        if (!vm.playNumber(number)) {
            Toast.makeText(context, context.getString(R.string.no_channel_number, number), Toast.LENGTH_SHORT).show()
        }
        digits = ""
    }
    LaunchedEffect(listVisible, guideVisible, playerState.problem, menuFor) {
        if (!listVisible && !guideVisible && menuFor == null && playerState.problem == null) rootFocus.requestFocusWhenReady()
    }

    // The panel opens on the list being watched, with the playing channel focused.
    LaunchedEffect(listVisible) {
        if (listVisible) {
            browse.selectGroup(listKey)
            previewed = current
        }
    }

    // Back closes the list first; otherwise navigation pops the player as usual.
    BackHandler(enabled = listVisible) { listVisible = false }
    BackHandler(enabled = guideVisible) { guideVisible = false }
    // Compose spends a Back press on moving focus out of a list or button before BackHandler sees it,
    // so Back needed two presses on the panel, the guide and the error card. Take it first instead.
    val onBackKey: () -> Unit = {
        when {
            guideVisible -> guideVisible = false
            listVisible -> listVisible = false
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onBackKey()
                true
            }
            .onKeyEvent { event ->
                // While the list is open, the D-pad belongs to the list.
                if (listVisible || guideVisible) return@onKeyEvent false
                if (event.key == Key.Guide && event.type == KeyEventType.KeyUp) {
                    guideVisible = true
                    return@onKeyEvent true
                }
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter
                // OK: a short press opens the channel list (on release); holding it opens the quick menu.
                if (isOk) {
                    when {
                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 && !okHeld -> {
                            okHeld = true
                            menuFor = current
                        }
                        event.type == KeyEventType.KeyUp -> if (okHeld) okHeld = false else listVisible = true
                    }
                    return@onKeyEvent true
                }
                if (event.key == Key.Menu) {
                    if (event.type == KeyEventType.KeyUp) menuFor = current
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    event.key == Key.DirectionUp || event.key == Key.ChannelUp -> { vm.next(); true }
                    event.key == Key.DirectionDown || event.key == Key.ChannelDown -> { vm.previous(); true }
                    event.key == Key.DirectionLeft -> { listVisible = true; true }
                    event.key == Key.DirectionRight || event.key == Key.Info -> { bannerToken++; true }
                    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        digits = (digits + (keyCode - AndroidKeyEvent.KEYCODE_0)).takeLast(4)
                        true
                    }
                    keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> {
                        digits = (digits + (keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0)).takeLast(4)
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(vm.livePlayer.player, Modifier.fillMaxSize())

        if (playerState.buffering && playerState.problem == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (showQuality) QualityBadge(stats, Modifier.align(Alignment.TopStart).padding(32.dp))

        // Hidden while browsing: its Try again button would take the remote away from the list.
        playerState.problem?.takeIf { !listVisible && !guideVisible }?.let { problem ->
            ProblemPanel(
                message = context.playbackProblemMessage(problem),
                detail = playerState.detail,
                onRetry = vm.livePlayer::retry,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = bannerVisible && !listVisible && current != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            current?.let { InfoBanner(it, position = index + 1, total = channels.size, group = groupName, programme = programme) }
        }

        AnimatedVisibility(
            visible = listVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(600.dp)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Color(0xF50E1117), Color(0xE60E1117))))
                        .padding(start = 32.dp, end = 16.dp, top = 27.dp, bottom = 27.dp),
                ) {
                    TvLiveContent(
                        onPlay = { channel, key ->
                            vm.playFrom(key, channel)
                            listVisible = false
                        },
                        onAddPlaylist = {},
                        groupWidth = 200.dp,
                        current = current?.ref,
                        queue = vm.queue,
                        onFocusChannel = { previewed = it },
                    )
                }
                previewed?.let { channel ->
                    ChannelPreview(
                        channel = channel,
                        programme = guide[channel.ref],
                        health = health[channel.ref],
                        isFavorite = channel.ref in favorites,
                        isPlaying = channel.ref == current?.ref,
                        modifier = Modifier.align(Alignment.Bottom).padding(24.dp),
                    )
                }
            }
        }

        if (guideVisible) {
            // The same timeline as the Guide section, over the video, which keeps playing.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xEB0E1117))
                    .padding(horizontal = TvSafeArea.Horizontal, vertical = TvSafeArea.Vertical),
            ) {
                TvGuideContent(
                    onPlay = { channel, key ->
                        vm.playFrom(key, channel)
                        guideVisible = false
                    },
                    current = current?.ref,
                )
            }
        }

        menuFor?.let { channel ->
            TvChannelEditor(
                channel = channel,
                watchlists = watchlists,
                edits = vm.edits,
                onDone = { menuFor = null },
                onNameTaken = { name ->
                    Toast.makeText(context, context.getString(R.string.name_taken, name), Toast.LENGTH_SHORT).show()
                },
                extraItems = listOf(
                    TvMenuItem(stringResource(R.string.player_open_guide)) { guideVisible = true },
                    TvMenuItem(stringResource(if (showQuality) R.string.quality_hide else R.string.quality_show), vm::toggleQuality),
                ),
            )
        }

        if (digits.isNotEmpty()) {
            Text(
                text = digits,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoBanner(channel: Channel, position: Int, total: Int, group: String?, programme: NowNext?) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel.name, channel.logoUrl, size = 72.dp)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${channel.number}  ${channel.name}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val now = programme?.now
                if (now == null) {
                    Text(
                        text = group ?: channel.groupTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = "${context.formatTimeRange(now)}   ${now.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    ProgramProgress(
                        fraction = programme.progress(System.currentTimeMillis()),
                        color = MaterialTheme.colorScheme.secondary,
                        track = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.width(420.dp),
                    )
                    programme.next?.let { next ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            context.nextLine(next),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.channel_position, position, total),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    stringResource(R.string.player_hint_tv),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** What the highlighted channel in the browse panel is showing, before switching to it. */
@Composable
private fun ChannelPreview(
    channel: Channel,
    programme: NowNext?,
    health: HealthStatus?,
    isFavorite: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xE60E1117), RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(channel.name, channel.logoUrl, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${channel.number}  ${channel.name}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        Text(
                            stringResource(R.string.preview_playing),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                    HealthBadge(
                        health,
                        textStyle = MaterialTheme.typography.labelLarge,
                        dotSize = 8.dp,
                        showWorking = true,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    if (isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.label_favorite),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        val now = programme?.now
        Spacer(Modifier.height(16.dp))
        if (now == null) {
            Text(
                stringResource(R.string.guide_nothing_now),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        } else {
            Text(
                "${context.formatTimeRange(now)}   ${now.title}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            ProgramProgress(
                fraction = programme.progress(System.currentTimeMillis()),
                color = MaterialTheme.colorScheme.secondary,
                track = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            )
            now.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            programme.next?.let { next ->
                Spacer(Modifier.height(8.dp))
                Text(
                    context.nextLine(next),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProblemPanel(message: String, detail: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(message) { retryFocus.requestFocusWhenReady() }
    Column(
        modifier = modifier
            .width(560.dp)
            .background(Color(0xE6171B24), RoundedCornerShape(20.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
            Text(stringResource(R.string.try_again))
        }
    }
}
