package com.iptvwhiz.app.ui.mobile

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.epg.NowNext
import com.iptvwhiz.app.ui.common.ProgramProgress
import com.iptvwhiz.app.ui.common.formatTimeRange
import com.iptvwhiz.app.ui.common.nextLine
import com.iptvwhiz.app.player.LivePlayerState
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.PlayerSurface
import com.iptvwhiz.app.ui.common.findActivity
import com.iptvwhiz.app.ui.common.playbackProblemMessage
import com.iptvwhiz.app.ui.state.PlayerViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val CONTROLS_MS = 4_000L

/**
 * Portrait: video on top, what's playing, then the channel list.
 * Landscape: full screen. Tap shows controls; swipe up/down on the video changes channel.
 */
@Composable
fun MobilePlayerScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, savedState -> PlayerViewModel(container, savedState) }
    val channels by vm.channels.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val playerState by vm.livePlayer.state.collectAsStateWithLifecycle()
    val groupName by vm.groupName.collectAsStateWithLifecycle()
    val programme by vm.programme.collectAsStateWithLifecycle()
    val watchlists by vm.watchlists.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Channel?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, current?.stableKey) {
        if (controlsVisible) {
            delay(CONTROLS_MS)
            controlsVisible = false
        }
    }

    val context = LocalContext.current
    // Rotation recreates the activity; the player lives in the ViewModel, so keep it playing through that.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (context.findActivity()?.isChangingConfigurations != true) vm.livePlayer.stop()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.livePlayer.resume() }
    ImmersiveMode(enabled = landscape)

    val video = @Composable { modifier: Modifier ->
        VideoArea(
            vm = vm,
            channel = current,
            state = playerState,
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            onLongPress = { current?.let { menuFor = it } },
            onBack = onBack,
            modifier = modifier,
        )
    }

    menuFor?.let { channel ->
        val context = LocalContext.current
        ChannelEditor(
            channel = channel,
            watchlists = watchlists,
            edits = vm.edits,
            onDone = { menuFor = null },
            onNameTaken = { name ->
                android.widget.Toast.makeText(context, context.getString(R.string.name_taken, name), android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (landscape) {
        video(Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.background(Color.Black).statusBarsPadding()) {
                video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            current?.let { NowPlaying(it, groupName, programme) }
            HorizontalDivider()
            PortraitChannelList(channels, index, onSelect = vm::playAt, onLongPress = { menuFor = it })
        }
    }
}

@Composable
private fun VideoArea(
    vm: PlayerViewModel,
    channel: Channel?,
    state: LivePlayerState,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }

    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }, onLongPress = { onLongPress() }) }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, amount -> total += amount },
                    onDragEnd = {
                        if (abs(total) > swipeThreshold) {
                            if (total < 0) vm.next() else vm.previous()
                        }
                    },
                )
            },
    ) {
        PlayerSurface(vm.livePlayer.player, Modifier.fillMaxSize(), textureView = true)

        if (state.buffering && state.problem == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        state.problem?.let { problem ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    context.playbackProblemMessage(problem),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                state.detail?.let {
                    Text(it, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm.livePlayer::retry) { Text(stringResource(R.string.try_again)) }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                    Text(
                        text = channel?.let { "${it.number}  ${it.name}" }.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoundControl(Icons.Filled.KeyboardArrowUp, stringResource(R.string.next_channel), vm::next)
                    RoundControl(Icons.Filled.KeyboardArrowDown, stringResource(R.string.previous_channel), vm::previous)
                }
            }
        }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color(0x80000000), RoundedCornerShape(24.dp)),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Composable
private fun NowPlaying(channel: Channel, group: String?, programme: NowNext?) {
    val context = LocalContext.current
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(channel.name, channel.logoUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "${channel.number}  ${channel.name}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val now = programme?.now
            if (now == null) {
                Text(
                    group ?: channel.groupTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${context.formatTimeRange(now)}  ${now.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgramProgress(
                    fraction = programme.progress(System.currentTimeMillis()),
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.width(200.dp).padding(vertical = 4.dp),
                )
                programme.next?.let {
                    Text(
                        context.nextLine(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitChannelList(
    channels: List<Channel>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onLongPress: (Channel) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(channels, key = { _, it -> it.stableKey }) { i, channel ->
            val isCurrent = i == currentIndex
            ListItem(
                leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 40.dp) },
                headlineContent = {
                    Text("${channel.number}  ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = if (isCurrent) {
                    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    ListItemDefaults.colors()
                },
                modifier = Modifier.combinedClickable(onClick = { onSelect(i) }, onLongClick = { onLongPress(channel) }),
            )
        }
    }
}

/** Hides the status and navigation bars in landscape; a swipe from the edge brings them back briefly. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
