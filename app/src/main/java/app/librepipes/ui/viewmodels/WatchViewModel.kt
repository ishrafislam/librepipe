package app.librepipes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
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
    /** Resolutions this video offers, highest first. */
    var availableHeights by mutableStateOf<List<Int>>(emptyList())
        private set
    var currentHeight by mutableStateOf(0)
        private set
    var playbackSpeed by mutableStateOf(1f)
        private set
    var captionsOn by mutableStateOf(false)
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
            runCatching { Extractor.stream(initialRef.url) }.getOrNull()?.let { stream ->
                info = stream
                availableHeights = Playback.availableHeights(stream)
                val settings = container.settings.snapshot()
                currentHeight = Playback
                    .selectStreams(stream, settings.audioOnly || initialRef.isAudio, settings.maxQuality)
                    .height
                captionsOn = settings.captionsEnabled
                applyCaptions(settings.captionsEnabled)
            }
        }
        viewModelScope.launch {
            playbackSpeed = container.settings.snapshot().playbackSpeed
            controller?.setPlaybackSpeed(playbackSpeed)
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

    /**
     * Rebuilds the current item at [height]. Without a DASH manifest there is no
     * seamless track switch, so this reloads and re-seeks — hence capturing position
     * and play state first, or a paused video would silently resume.
     */
    fun setQuality(height: Int) {
        val c = controller ?: return
        val stream = info ?: return
        viewModelScope.launch {
            container.settings.setMaxQuality(height)
            val position = c.currentPosition
            val wasPlaying = c.isPlaying
            val audioOnly = container.settings.snapshot().audioOnly || ref.isAudio
            val item = Playback.buildItem(stream, ref, audioOnly, height)
            c.setMediaItem(item, position)
            c.prepare()
            if (wasPlaying) c.play()
            currentHeight = Playback.selectStreams(stream, audioOnly, height).height
            applyCaptions(captionsOn)
        }
    }

    fun changeSpeed(value: Float) {
        playbackSpeed = value
        controller?.setPlaybackSpeed(value)
        viewModelScope.launch { container.settings.setPlaybackSpeed(value) }
    }

    fun toggleCaptions() {
        val on = !captionsOn
        captionsOn = on
        applyCaptions(on)
        viewModelScope.launch { container.settings.setCaptionsEnabled(on) }
    }

    /** Text tracks are always attached; this flips whether they render. */
    private fun applyCaptions(on: Boolean) {
        val c = controller ?: return
        val params = c.trackSelectionParameters
        c.trackSelectionParameters = params.buildUpon()
            .setDisabledTrackTypes(if (on) emptySet() else setOf(C.TRACK_TYPE_TEXT))
            .build()
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
        c.setPlaybackSpeed(playbackSpeed)
        applyCaptions(captionsOn)
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
