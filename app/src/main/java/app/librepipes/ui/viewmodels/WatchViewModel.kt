package app.librepipes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.StreamRef
import app.librepipes.data.youtube.StreamInfo
import app.librepipes.data.youtube.WatchNext
import app.librepipes.di.AppContainer
import app.librepipes.player.HistoryTracker
import app.librepipes.player.Playback
import app.librepipes.player.PlaybackOpener
import app.librepipes.util.AppError
import app.librepipes.util.toAppError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drives the watch route. Owns a [MediaController] onto the shared playback session
 * (the one ExoPlayer lives in `PlaybackService`), plus the page metadata.
 *
 * Playback and metadata are deliberately decoupled: the session is started first and
 * the `next` request fills the channel block in when it lands.
 */
class WatchViewModel(
    private val container: AppContainer,
    private val initialRef: StreamRef,
    private val queue: List<StreamRef>,
) : ViewModel() {

    var ref by mutableStateOf(initialRef)
        private set
    var info by mutableStateOf<StreamInfo?>(null)
        private set
    var watchNext by mutableStateOf<WatchNext?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var position by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var hasPrev by mutableStateOf(false)
        private set
    /** Chapter start points as 0..1 fractions, for the seek bar ticks. */
    var chapters by mutableStateOf<List<Float>>(emptyList())
        private set
    var subscribed by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    /** Epoch millis when an upcoming premiere starts; null for normal videos. */
    var premiereAt by mutableStateOf<Long?>(null)
        private set
    /** The connected session, for `PlayerView` to render. Null until the connect lands. */
    var player by mutableStateOf<Player?>(null)
        private set

    private var controller: MediaController? = null
    private var chapterSeconds: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            val resolved = runCatching {
                PlaybackOpener.startSession(container.appContext, initialRef, queue.ifEmpty { listOf(initialRef) })
            }
            resolved.exceptionOrNull()?.let { error = it.toAppError() }
            premiereAt = resolved.getOrNull()?.premiereAt
            connect()
        }
        viewModelScope.launch {
            runCatching { Extractor.stream(initialRef.url) }.getOrNull()?.let { info = it }
        }
        viewModelScope.launch {
            runCatching { Extractor.watchNext(initialRef.url) }.getOrNull()?.let { watchNext = it }
        }
        viewModelScope.launch {
            chapterSeconds = runCatching { Extractor.chapters(initialRef.url) }
                .getOrDefault(emptyList())
                .map { it.startSeconds }
            recomputeChapters()
        }
        viewModelScope.launch {
            container.subscriptions.observeAll().collect { subs ->
                val url = channelUrl()
                subscribed = url != null && subs.any { it.channelUrl == url }
            }
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun prev() {
        controller?.seekToPreviousMediaItem()
    }

    /** [fraction] is 0..1 across the current item. */
    fun seekTo(fraction: Float) {
        val c = controller ?: return
        val d = c.duration
        if (d > 0) c.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun download() = viewModelScope.launch {
        container.downloadManager.enqueue(ref, DownloadMode.VIDEO)
    }

    fun toggleSubscribe() = viewModelScope.launch {
        val channel = channelRef() ?: return@launch
        if (subscribed) {
            container.subscriptions.unsubscribe(channel.url)
        } else {
            container.subscriptions.subscribe(channel)
        }
    }

    /** The channel behind this video, assembled from whichever source has landed. */
    fun channelRef(): ChannelRef? {
        val url = channelUrl() ?: return null
        val next = watchNext
        return ChannelRef(
            id = next?.uploaderId ?: url.substringAfterLast('/'),
            name = next?.uploaderName ?: info?.uploaderName ?: ref.uploaderName.orEmpty(),
            url = url,
            avatarUrl = next?.uploaderAvatarUrl ?: ref.uploaderAvatarUrl,
        )
    }

    private fun channelUrl(): String? =
        watchNext?.uploaderUrl ?: info?.uploaderUrl ?: ref.uploaderUrl

    private suspend fun connect() {
        val c = runCatching { PlaybackOpener.connect(container.appContext) }.getOrNull() ?: return
        controller = c
        player = c
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh(player)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.extras?.getString(Playback.EXTRA_REF_JSON)
                    ?.let { StreamRef.fromJson(it) }
                    ?.let { ref = it }
                refresh(c)
            }
        })
        HistoryTracker.start(container.appContext, c, viewModelScope)
        refresh(c)
        while (true) {
            delay(500)
            refresh(c)
        }
    }

    private fun refresh(player: Player) {
        isPlaying = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
        position = player.currentPosition.coerceAtLeast(0L)
        val d = player.duration
        val known = if (d > 0) d else 0L
        if (known != duration) {
            duration = known
            recomputeChapters()
        }
        hasNext = player.hasNextMediaItem()
        hasPrev = player.hasPreviousMediaItem()
    }

    /**
     * Fractions need a duration, and the chapter fetch usually finishes first. Recompute
     * on both, or the ticks silently never appear.
     */
    private fun recomputeChapters() {
        val seconds = duration / 1000f
        chapters = if (seconds <= 0f || chapterSeconds.isEmpty()) {
            emptyList()
        } else {
            chapterSeconds.mapNotNull { start ->
                (start / seconds).takeIf { it in 0.001f..1f }
            }
        }
    }

    override fun onCleared() {
        player = null
        controller?.release()
        controller = null
        super.onCleared()
    }
}
