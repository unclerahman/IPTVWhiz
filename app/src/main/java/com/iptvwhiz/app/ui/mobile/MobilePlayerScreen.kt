package com.iptvwhiz.app.ui.mobile

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.iptvwhiz.app.player.PlaybackProblem
import com.iptvwhiz.app.player.StreamStats
import com.iptvwhiz.app.ui.common.PlayerIcons
import com.iptvwhiz.app.ui.common.QualityBadge
import com.iptvwhiz.app.ui.common.ChannelLogo
import com.iptvwhiz.app.ui.common.PlayerSurface
import com.iptvwhiz.app.ui.common.findActivity
import com.iptvwhiz.app.ui.common.playbackProblemMessage
import com.iptvwhiz.app.ui.state.PlayerViewModel
import com.iptvwhiz.app.ui.state.appViewModel
import com.iptvwhiz.app.ui.theme.Brand
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
    val showQuality by vm.showQuality.collectAsStateWithLifecycle()
    val stats by vm.livePlayer.stats.collectAsStateWithLifecycle()
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

    // Full screen = landscape, even when the phone's auto-rotate is off. Leaving the player gives rotation back.
    val toggleFullScreen = {
        context.findActivity()?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            // Rotating recreates the activity and disposes this too; that must not undo the rotation.
            if (activity?.isChangingConfigurations != true) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val video = @Composable { modifier: Modifier ->
        VideoArea(
            vm = vm,
            channel = current,
            state = playerState,
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            onLongPress = { current?.let { menuFor = it } },
            showQuality = showQuality,
            stats = stats,
            fullScreen = landscape,
            onToggleQuality = vm::toggleQuality,
            onToggleFullScreen = toggleFullScreen,
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
    showQuality: Boolean,
    stats: StreamStats,
    fullScreen: Boolean,
    onToggleQuality: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
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
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.player_connecting),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        state.problem?.let { problem ->
            PlaybackProblemCard(
                problem = problem,
                detail = state.detail,
                onRetry = vm.livePlayer::retry,
                onNext = vm::next,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
        }

        if (showQuality && state.problem == null) QualityBadge(stats, Modifier.align(Alignment.BottomStart).padding(8.dp))

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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        channel?.let {
                            Text(
                                it.number.toString(),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = channel?.name.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onToggleQuality) {
                        Icon(
                            Icons.Filled.Info,
                            stringResource(if (showQuality) R.string.quality_hide else R.string.quality_show),
                            tint = if (showQuality) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = onToggleFullScreen) {
                        Icon(
                            if (fullScreen) PlayerIcons.FullscreenExit else PlayerIcons.Fullscreen,
                            stringResource(if (fullScreen) R.string.exit_full_screen else R.string.full_screen),
                            tint = Color.White,
                        )
                    }
                }
                if (state.problem == null) {
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
private fun PlaybackProblemCard(
    problem: PlaybackProblem,
    detail: String?,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Compact on purpose: in portrait this card sits inside a 16:9 video area, so the message,
    // the diagnostics line and both actions have to fit without scrolling.
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE6141428))
            .border(1.dp, Brand.IndigoBorder.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 1.dp).size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                context.playbackProblemMessage(problem),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onNext) { Text(stringResource(R.string.next_channel)) }
        }
    }
}

/** Green "LIVE" pill: the one place the on-air accent is used in the player. */
@Composable
private fun LivePill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.live_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun NowPlaying(channel: Channel, group: String?, programme: NowNext?) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.name, channel.logoUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePill()
                Spacer(Modifier.width(8.dp))
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append('#').append(channel.number)
                    val meta = group ?: channel.groupTitle
                    if (meta.isNotBlank()) append("  ·  ").append(meta)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (programme != null) {
                val now = programme.now
                if (now != null) {
                    Spacer(Modifier.height(8.dp))
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(channels, key = { _, it -> it.stableKey }) { i, channel ->
            val isCurrent = i == currentIndex
            Box {
                ListItem(
                    leadingContent = { ChannelLogo(channel.name, channel.logoUrl, size = 40.dp) },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = 22.dp),
                            )
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    ),
                    modifier = Modifier.combinedClickable(onClick = { onSelect(i) }, onLongClick = { onLongPress(channel) }),
                )
                if (isCurrent) {
                    // Left accent bar: the playing channel is obvious without painting the whole row.
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .width(3.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                }
            }
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
