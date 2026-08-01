package app.librepipes.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import app.librepipes.LibrePipeApp
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.StreamRef
import app.librepipes.data.repo.PlaylistRepository
import app.librepipes.ui.components.AddToPlaylistDialog
import app.librepipes.ui.theme.LibrePipeTheme
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NowPlayingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STREAM_JSON = "stream_json"
        const val EXTRA_QUEUE_JSON = "queue_json"
    }

    private var controllerForPip: MediaController? = null
    private var pipMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialRef = StreamRef.fromJson(intent?.getStringExtra(EXTRA_STREAM_JSON))
        val queue = intent?.getStringArrayListExtra(EXTRA_QUEUE_JSON)
            ?.mapNotNull { StreamRef.fromJson(it) } ?: emptyList()

        setContent {
            LibrePipeTheme {
                NowPlayingScreen(
                    onBack = { finish() },
                    initialRef = initialRef,
                    initialQueue = queue,
                    pipMode = pipMode,
                    onControllerChanged = { controllerForPip = it },
                    onEnterPip = { enterPip() },
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = controllerForPip
        if (c != null && c.playbackState == Player.STATE_READY) enterPip()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        pipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            // Re-show content
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    initialRef: StreamRef?,
    initialQueue: List<StreamRef>,
    pipMode: Boolean,
    onControllerChanged: (MediaController?) -> Unit,
    onEnterPip: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LibrePipeApp
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var audioOnly by remember { mutableStateOf(false) }
    var subtitlesAvailable by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var queueRefs by remember { mutableStateOf(initialQueue.ifEmpty { listOf(initialRef).filterNotNull() }) }
    val scope = rememberCoroutineScope()
    val container = app.container

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var hasNext by remember { mutableStateOf(false) }
    var hasPrev by remember { mutableStateOf(false) }

    fun currentRef(): StreamRef? = controller?.currentMediaItem
        ?.mediaMetadata?.extras?.getString(Playback.EXTRA_REF_JSON)
        ?.let { StreamRef.fromJson(it) }

    LaunchedEffect(Unit) {
        val c = PlaybackOpener.connect(context)
        controller = c
        onControllerChanged(c)

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                position = player.currentPosition
                duration = player.duration
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val ref = currentRef()
                audioOnly = ref?.isAudio == true
                subtitlesAvailable = false
            }
        }
        c.addListener(listener)
        audioOnly = currentRef()?.isAudio == true
        subtitlesAvailable = currentRef()?.isAudio == false

        // Start playback when opened with a stream that isn't the current one
        if (initialRef != null && c.currentMediaItem?.mediaId != initialRef.id) {
            PlaybackOpener.startSession(context, initialRef, initialQueue)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val c = controller
            if (c != null) {
                scope.launch { HistoryTracker.recordCurrent(context, c) }
                c.release()
            }
            onControllerChanged(null)
        }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        HistoryTracker.start(context, c, scope)
        while (true) {
            delay(500)
            position = c.currentPosition
            duration = c.duration
            isPlaying = c.isPlaying
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (audioOnly) {
            AudioOnlyPlayer(
                ref = currentRef(),
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                hasNext = hasNext,
                hasPrev = hasPrev,
                onPlayPause = { controller?.playOrPause() },
                onNext = { controller?.seekToNextMediaItem() },
                onPrev = { controller?.seekToPreviousMediaItem() },
                onSeek = { controller?.seekTo(it) },
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerShowTimeoutMs = 0
                        controllerHideOnTouch = true
                        setShutterBackgroundColor(AndroidColor.BLACK)
                    }
                },
                update = { playerView ->
                    playerView.player = controller
                    playerView.keepScreenOn = isPlaying
                },
            )
        }

        if (!pipMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x78000000),
                            0.18f to Color.Transparent
                        )
                    )
            ) {
                val ref = currentRef()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ref?.title ?: "Now playing",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ref?.uploaderName ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitlesAvailable) {
                        IconButton(onClick = { controller?.toggleSubtitles() }) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { audioOnly = !audioOnly }) {
                        Icon(
                            if (audioOnly) Icons.Rounded.GraphicEq else Icons.Rounded.Headphones,
                            contentDescription = "Audio only",
                            tint = if (audioOnly) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (!audioOnly) {
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Rounded.PictureInPicture, contentDescription = "Picture in picture", tint = Color.White)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color(0x8C000000)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val ref = currentRef()
                IconButton(onClick = {
                    if (ref != null) {
                        scope.launch {
                            container.downloadManager.enqueue(ref, DownloadMode.VIDEO)
                        }
                    }
                }) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download", tint = Color.White)
                }
                IconButton(onClick = {
                    if (ref != null) {
                        queueRefs = queueRefs.ifEmpty { listOf(ref) }
                        showPlaylistDialog = true
                    }
                }) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White)
                }
                IconButton(onClick = {
                    if (ref != null) {
                        scope.launch {                                PlaybackOpener.startSession(context, ref, queueRefs.ifEmpty { listOf(ref) })
                                PopupLauncher.requestAndStart(context, ref, queueRefs)
                        }
                    }
                }) {
                    Icon(Icons.Rounded.PictureInPicture, contentDescription = "Popup player", tint = Color.White)
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Color.White)
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            val items = (0 until (controller?.mediaItemCount ?: 0)).mapNotNull { index ->
                controller?.getMediaItemAt(index)
            }
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(items) { item ->
                    val ref = item.mediaMetadata.extras?.getString(Playback.EXTRA_REF_JSON)
                        ?.let { StreamRef.fromJson(it) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = (0 until (controller?.mediaItemCount ?: 0)).firstOrNull { i ->
                                    controller?.getMediaItemAt(i)?.mediaId == item.mediaId
                                } ?: -1
                                if (idx >= 0) controller?.seekTo(idx, 0L)
                                showQueue = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (item.mediaId == controller?.currentMediaItem?.mediaId) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(ref?.title ?: item.mediaId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        val ref = currentRef()
        if (ref != null) {
            AddToPlaylistDialog(
                context = context,
                ref = ref,
                onDismiss = { showPlaylistDialog = false },
            )
        }
    }
}

@Composable
private fun AudioOnlyPlayer(
    ref: StreamRef?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (ref?.thumbnailUrl != null) {
                    AsyncImage(
                        model = ref.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = ref?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ref?.uploaderName ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            Slider(
                value = position.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(Format.time(position), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Text(Format.time(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = hasPrev) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}

object PopupLauncher {
    private var pendingRef: StreamRef? = null
    private var pendingQueue: List<StreamRef> = emptyList()

    fun requestAndStart(context: Context, ref: StreamRef, queue: List<StreamRef>) {
        if (android.provider.Settings.canDrawOverlays(context)) {
            context.startService(
                Intent(context, PopupPlayerService::class.java)
                    .putExtra(PopupPlayerService.EXTRA_REF_JSON, ref.toJson())
                    .putStringArrayListExtra(
                        PopupPlayerService.EXTRA_QUEUE_JSON,
                        ArrayList(queue.map { it.toJson() })
                    )
            )
        } else {
            pendingRef = ref
            pendingQueue = queue
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun consumePending(context: Context): Boolean {
        val ref = pendingRef ?: return false
        pendingRef = null
        if (android.provider.Settings.canDrawOverlays(context)) {
            requestAndStart(context, ref, pendingQueue)
            pendingQueue = emptyList()
            return true
        }
        return false
    }
}
