package app.librepipes.player

import android.content.Context
import androidx.media3.common.Player
import app.librepipes.LibrePipeApp
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Records watch history from the shared session while playing.
 */
object HistoryTracker {

    /** Starts a loop that periodically writes the current position. */
    fun start(context: Context, controller: Player, scope: CoroutineScope) {
        val app = context.applicationContext as LibrePipeApp
        scope.launch {
            if (!app.container.settings.recordHistory.first()) return@launch
            while (true) {
                delay(5_000)
                if (!controller.isPlaying) continue
                recordCurrent(app, controller)
            }
        }
    }

    /** Writes one final history entry (used on pause / leaving the player). */
    suspend fun recordCurrent(context: Context, controller: Player) {
        val app = context.applicationContext as LibrePipeApp
        recordCurrent(app, controller)
    }

    private suspend fun recordCurrent(app: LibrePipeApp, controller: Player) {
        if (!app.container.settings.recordHistory.first()) return
        val item = controller.currentMediaItem ?: return
        val refJson = item.mediaMetadata.extras?.getString(Playback.EXTRA_REF_JSON) ?: return
        val ref = StreamRef.fromJson(refJson) ?: return
        val position = controller.currentPosition
        val duration = controller.duration
        if (position <= 0) return
        app.container.history.record(
            ref = ref,
            positionMs = position,
            durationMs = if (duration >= 0) duration else 0L,
        )
    }
}
