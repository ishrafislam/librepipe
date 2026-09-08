package app.librepipes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import app.librepipes.data.model.StreamRef
import app.librepipes.di.AppContainer
import app.librepipes.player.Playback
import app.librepipes.player.PlaybackOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the [app.librepipes.ui.components.kit.LpMiniPlayer] docked above the
 * bottom bar: observes the shared playback session and mirrors its state.
 * Hidden while nothing is playing or when the session is idle.
 */
class MiniPlayerViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val ref: StreamRef? = null,
        val title: String = "",
        val channelName: String? = null,
        val thumbnailUrl: String? = null,
        val progress: Float = 0f,
        val isLive: Boolean = false,
        val isPlaying: Boolean = false,
        val visible: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private var controller: MediaController? = null

    init {
        viewModelScope.launch {
            val c = runCatching { PlaybackOpener.connect(container.appContext) }.getOrNull()
                ?: return@launch
            controller = c
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(player)

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh(c)
            }
            c.addListener(listener)
            refresh(c)
            while (true) {
                delay(500)
                refresh(c)
            }
        }
    }

    /** Refreshes straight after toggling so the icon flips without waiting for the poll. */
    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        refresh(c)
    }

    /** Stops playback and clears the session so the mini player disappears. */
    fun stop() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        refresh(c)
    }

    private fun refresh(player: Player) {
        if (player.playbackState == Player.STATE_IDLE) {
            _uiState.update { it.copy(ref = null, visible = false) }
            return
        }
        val item = player.currentMediaItem
        if (item == null) {
            _uiState.update { it.copy(ref = null, visible = false) }
            return
        }
        val ref = item.mediaMetadata.extras?.getString(Playback.EXTRA_REF_JSON)
            ?.let { StreamRef.fromJson(it) }
        // The timeline window is authoritative: a live HLS stream with a DVR window
        // reports a real duration, so a position/duration fraction would draw a
        // timeline nobody can scrub. `ref` covers deep links that never carried the flag.
        val live = player.isCurrentMediaItemLive || ref?.isLive == true
        val duration = player.duration
        val progress = if (!live && duration > 0) {
            (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        _uiState.update {
            it.copy(
                ref = ref,
                title = item.mediaMetadata.title?.toString() ?: ref?.title ?: "",
                channelName = item.mediaMetadata.artist?.toString() ?: ref?.uploaderName,
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: ref?.thumbnailUrl,
                progress = progress,
                isLive = live,
                isPlaying = player.isPlaying,
                visible = true,
            )
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
