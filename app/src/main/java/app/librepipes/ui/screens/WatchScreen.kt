package app.librepipes.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.AddToPlaylistDialog
import app.librepipes.ui.components.kit.LpChannelRow
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpSeekBar
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpSwitch
import app.librepipes.ui.theme.Spacing
import app.librepipes.ui.viewmodels.WatchViewModel
import app.librepipes.util.Format
import kotlinx.coroutines.delay
import android.graphics.Color as AndroidColor

/** How long the overlay stays up after the last touch. */
private const val OVERLAY_TIMEOUT_MS = 3_000L

@Composable
fun WatchScreen(
    vm: WatchViewModel,
    fullscreen: Boolean,
    pipMode: Boolean,
    locked: Boolean,
    onMinimize: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    // In PiP the system shows only our window content, so render the bare surface.
    if (pipMode) {
        VideoSurface(vm, Modifier.fillMaxSize())
        return
    }

    // Order matters: unlocking must win over leaving fullscreen, or back would escape
    // a screen the user deliberately locked.
    BackHandler(enabled = fullscreen && !locked) { onToggleFullscreen() }
    BackHandler(enabled = locked) { onSetLocked(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        VideoBox(
            vm = vm,
            fullscreen = fullscreen,
            locked = locked,
            onMinimize = onMinimize,
            onEnterPip = onEnterPip,
            onToggleFullscreen = onToggleFullscreen,
            onLock = { onSetLocked(true) },
            onUnlock = { onSetLocked(false) },
            modifier = if (fullscreen) Modifier.weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        if (!fullscreen) {
            DetailSections(vm = vm, onOpenChannel = onOpenChannel)
        }
    }
}

@Composable
private fun VideoBox(
    vm: WatchViewModel,
    fullscreen: Boolean,
    locked: Boolean,
    onMinimize: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayVisible by remember { mutableStateOf(true) }
    var showOptions by remember { mutableStateOf(false) }
    // Any interaction restarts the countdown; a paused video keeps its controls, since
    // hiding them there would leave the user with nothing to press.
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastTouch, overlayVisible, vm.isPlaying) {
        if (overlayVisible && vm.isPlaying) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                overlayVisible = !overlayVisible
                lastTouch = System.currentTimeMillis()
            },
    ) {
        val error = vm.error
        val premiere = vm.premiereAt
        val chromeVisible = overlayVisible && error == null && premiere == null
        when {
            error != null -> PlayerErrorFrame(error)
            premiere != null -> PremiereFrame(vm.ref, premiere)
            else -> VideoSurface(vm, Modifier.fillMaxSize())
        }

        if (vm.buffering && error == null && premiere == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerOverlay(
                vm = vm,
                fullscreen = fullscreen,
                onMinimize = onMinimize,
                onEnterPip = onEnterPip,
                onToggleFullscreen = onToggleFullscreen,
                onOpenOptions = { showOptions = true },
                onInteract = { lastTouch = System.currentTimeMillis() },
            )
        }

        // Locked: no controls at all, just a way back out. Shares the 3s timer so a
        // locked screen actually stays clean.
        AnimatedVisibility(
            visible = chromeVisible && locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = "Tap to unlock",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .padding(bottom = Spacing.space6)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onUnlock() }
                    .padding(horizontal = Spacing.space4, vertical = 10.dp),
            )
        }
    }

    if (showOptions) {
        PlayerOptionsSheet(
            vm = vm,
            onDismiss = { showOptions = false },
            onLock = {
                showOptions = false
                if (!fullscreen) onToggleFullscreen()
                onLock()
            },
        )
    }
}

@Composable
private fun VideoSurface(vm: WatchViewModel, modifier: Modifier = Modifier) {
    // media3-ui-compose is not a dependency; PlayerView with its own controller
    // disabled is the supported route.
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(AndroidColor.BLACK)
            }
        },
        update = { view ->
            view.player = vm.player
            view.keepScreenOn = vm.isPlaying
            // Always fit: scale to the surface without cropping. ZOOM fills the screen
            // but pushes the top and bottom of the frame off it.
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        // Watch -> watch composes the next PlayerView before disposing this one, so
        // without this both stay bound to the same player and a dying surface can race
        // the live one.
        onRelease = { it.player = null },
    )
}

@Composable
private fun PlayerOverlay(
    vm: WatchViewModel,
    fullscreen: Boolean,
    onMinimize: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenOptions: () -> Unit,
    onInteract: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        OverlayIcon(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "Minimize",
            onClick = { onInteract(); onMinimize() },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIcon(
                icon = Icons.Rounded.PictureInPictureAlt,
                contentDescription = "Picture in picture",
                onClick = { onInteract(); onEnterPip() },
            )
            OverlayIcon(
                icon = Icons.Rounded.MoreVert,
                contentDescription = "More options",
                onClick = { onInteract(); onOpenOptions() },
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            // A live broadcast has no queue position to step through, whatever else the
            // session happens to be holding.
            if (!vm.isLive) {
                OverlayIcon(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    enabled = vm.hasPrev,
                    onClick = { onInteract(); vm.prev() },
                )
            }
            OverlayIcon(
                icon = if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (vm.isPlaying) "Pause" else "Play",
                size = 64.dp,
                onClick = { onInteract(); vm.playPause() },
            )
            if (!vm.isLive) {
                OverlayIcon(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    enabled = vm.hasNext,
                    onClick = { onInteract(); vm.next() },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.isLive) {
                    LiveIndicator(modifier = Modifier.weight(1f))
                } else {
                    Text(
                        text = "${Format.time(vm.position)} / ${Format.time(vm.duration)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                }
                OverlayIcon(
                    icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                    onClick = { onInteract(); onToggleFullscreen() },
                )
            }
            // A live stream has no timeline to scrub, so the bar would sit pinned at zero
            // and invite a seek that cannot happen.
            if (!vm.isLive) {
                LpSeekBar(
                    progress = if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f,
                    onSeek = { onInteract(); vm.seekTo(it) },
                    chapters = vm.chapters,
                )
            }
        }
    }
}

private enum class OptionsPage { MENU, QUALITY, SPEED }

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Drill-down player options: a short root list, then a page per setting.
 *
 * The page swap runs through [AnimatedContent] so both pages are composed during the
 * transition. Replacing the sheet's whole subtree in a single frame lets it re-measure
 * through an empty state and settle to Hidden, which fires onDismissRequest — that is
 * what previously closed the sheet instead of navigating.
 */
@Composable
private fun PlayerOptionsSheet(
    vm: WatchViewModel,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
) {
    var page by remember { mutableStateOf(OptionsPage.MENU) }
    LpSheet(
        title = when (page) {
            OptionsPage.MENU -> "Options"
            OptionsPage.QUALITY -> "Quality"
            OptionsPage.SPEED -> "Playback speed"
        },
        onDismiss = onDismiss,
        onBack = if (page == OptionsPage.MENU) null else ({ page = OptionsPage.MENU }),
    ) {
        AnimatedContent(targetState = page, label = "options-page") { current ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (current) {
                    OptionsPage.MENU -> {
                        // None of these do anything on a live stream: no quality ladder
                        // is exposed for the HLS playlist, live responses carry no caption
                        // tracks, and speed at the live edge only stalls or drifts.
                        if (!vm.isLive) {
                            OptionRow(
                                label = "Quality",
                                value = vm.currentHeight.takeIf { it > 0 }?.let { "${it}p" },
                                enabled = vm.availableHeights.isNotEmpty(),
                                onClick = { page = OptionsPage.QUALITY },
                            )
                            OptionRow(
                                label = "Playback speed",
                                value = speedLabel(vm.playbackSpeed),
                                onClick = { page = OptionsPage.SPEED },
                            )
                            OptionRow(
                                label = "Captions",
                                onClick = { vm.toggleCaptions() },
                                trailing = {
                                    LpSwitch(
                                        checked = vm.captionsOn,
                                        onCheckedChange = { vm.toggleCaptions() },
                                    )
                                },
                            )
                        }
                        OptionRow(label = "Lock screen", onClick = onLock)
                    }

                    OptionsPage.QUALITY -> vm.availableHeights.forEach { height ->
                        ChoiceRow(
                            label = "${height}p",
                            selected = height == vm.currentHeight,
                            onClick = {
                                vm.setQuality(height)
                                onDismiss()
                            },
                        )
                    }

                    OptionsPage.SPEED -> SPEEDS.forEach { value ->
                        ChoiceRow(
                            label = speedLabel(value),
                            selected = value == vm.playbackSpeed,
                            onClick = {
                                vm.changeSpeed(value)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.space6, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space6, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun speedLabel(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}.0x" else "${value}x"

/** Red dot + LIVE, standing in for the timestamp on a live stream. */
@Composable
private fun LiveIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF4B3E)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    enabled: Boolean = true,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(size * 0.25f),
    )
}

@Composable
private fun DetailSections(vm: WatchViewModel, onOpenChannel: (String) -> Unit) {
    val context = LocalContext.current
    var descriptionExpanded by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val next = vm.watchNext
    val info = vm.info
    val channel = vm.channelRef()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space4),
    ) {
        Spacer(Modifier.height(Spacing.space3))
        Text(
            text = info?.title ?: vm.ref.title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Spacing.space1))
        Text(
            text = metaLine(
                views = info?.viewCount ?: vm.ref.viewCount,
                // The absolute date only arrives with `next`; show the relative one meanwhile.
                date = next?.dateText ?: vm.ref.textualDate,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.space3))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save") { showPlaylistSheet = true }
            ActionChip(Icons.Rounded.Download, "Download") { vm.download() }
            ActionChip(Icons.Rounded.Share, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.ref.url)
                    putExtra(Intent.EXTRA_SUBJECT, info?.title ?: vm.ref.title)
                }
                context.startActivity(Intent.createChooser(send, "Share video"))
            }
        }

        Spacer(Modifier.height(Spacing.space4))
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.space2))

        if (channel != null) {
            LpChannelRow(
                channel = channel,
                onClick = { onOpenChannel(channel.url) },
                subtitle = next?.subscriberText,
                trailingLabel = if (vm.subscribed) "Subscribed" else "Subscribe",
                trailingFilled = !vm.subscribed,
                onTrailingClick = { vm.toggleSubscribe() },
            )
            Spacer(Modifier.height(Spacing.space2))
            HorizontalDivider()
        }

        val description = info?.description
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.space3))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (descriptionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (descriptionExpanded) "Collapse description" else "Expand description",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { descriptionExpanded = !descriptionExpanded }
                        .padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.space8))
    }

    if (showPlaylistSheet) {
        AddToPlaylistDialog(
            context = context,
            ref = vm.ref,
            onDismiss = { showPlaylistSheet = false },
        )
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.space2))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun metaLine(views: Long, date: String?): String {
    val parts = mutableListOf<String>()
    if (views > 0) parts += "${Format.count(views)} views"
    if (!date.isNullOrBlank()) parts += date
    return parts.joinToString(" · ")
}

@Composable
private fun PlayerErrorFrame(error: app.librepipes.util.AppError) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LpErrorState(
            title = when (error.code) {
                app.librepipes.util.AppError.OFFLINE -> "You're offline"
                app.librepipes.util.AppError.PRIVATE -> "Private video"
                app.librepipes.util.AppError.REMOVED -> "Video unavailable"
                app.librepipes.util.AppError.TIMEOUT -> "Timed out"
                else -> "Can't play this video"
            },
            message = error.message,
            code = error.code,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PremiereFrame(ref: StreamRef, premiereAt: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(premiereAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = (premiereAt - now).coerceAtLeast(0L)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(text = "Premiere", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                text = ref.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = premiereCountdown(remaining),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

private fun premiereCountdown(remainingMs: Long): String {
    if (remainingMs <= 0) return "Starting now"
    val totalMinutes = remainingMs / 60_000
    return if (totalMinutes >= 60) {
        "in ${totalMinutes / 60}h ${totalMinutes % 60}m"
    } else {
        "in ${totalMinutes}m ${(remainingMs % 60_000) / 1000}s"
    }
}
