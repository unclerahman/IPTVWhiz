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
import com.iptvwhiz.app.data.db.ChannelEntity
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.PlayerSurface
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

    var listVisible by remember { mutableStateOf(false) }
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
    LaunchedEffect(listVisible, playerState.problem) {
        if (!listVisible && playerState.problem == null) rootFocus.requestFocusWhenReady()
    }

    // Back closes the list first; otherwise navigation pops the player as usual.
    BackHandler(enabled = listVisible) { listVisible = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                // While the list is open, the D-pad belongs to the list.
                if (listVisible || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    event.key == Key.DirectionUp || event.key == Key.ChannelUp -> { vm.next(); true }
                    event.key == Key.DirectionDown || event.key == Key.ChannelDown -> { vm.previous(); true }
                    event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.DirectionLeft || event.key == Key.Menu -> { listVisible = true; true }
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

        playerState.problem?.let { problem ->
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
            current?.let { InfoBanner(it, position = index + 1, total = channels.size, group = vm.group) }
        }

        AnimatedVisibility(
            visible = listVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            ChannelListOverlay(
                title = vm.group ?: stringResource(R.string.all_channels),
                channels = channels,
                currentIndex = index,
                onSelect = { i ->
                    vm.playAt(i)
                    listVisible = false
                },
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
private fun InfoBanner(channel: ChannelEntity, position: Int, total: Int, group: String?) {
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
                Text(
                    text = group ?: channel.groupTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
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

@Composable
private fun ChannelListOverlay(
    title: String,
    channels: List<ChannelEntity>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val currentFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 4).coerceAtLeast(0))
    LaunchedEffect(Unit) { currentFocus.requestFocusWhenReady() }

    Column(
        Modifier
            .width(480.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF20E1117), Color(0xD90E1117))))
            .padding(start = 40.dp, end = 16.dp, top = 27.dp, bottom = 27.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
        )
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(channels, key = { _, it -> it.stableKey }) { i, channel ->
                ListItem(
                    selected = i == currentIndex,
                    onClick = { onSelect(i) },
                    modifier = if (i == currentIndex) Modifier.focusRequester(currentFocus) else Modifier,
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.number.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(48.dp),
                            )
                            ChannelLogo(channel.name, channel.logoUrl, size = 36.dp)
                        }
                    },
                    headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
