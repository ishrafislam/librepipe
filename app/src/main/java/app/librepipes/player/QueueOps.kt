package app.librepipes.player

import android.content.Context
import androidx.media3.common.Player
import app.librepipes.LibrePipeApp
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Appending to the playing queue from anywhere in the app.
 *
 * The ExoPlayer timeline *is* the queue — items already carry their [StreamRef] in
 * [Playback.EXTRA_REF_JSON], so nothing needs to be mirrored on the side. Editing an
 * existing queue (move, remove, clear) runs off the watch page's own controller; only
 * appending has to work from screens that hold no controller of their own.
 */
object QueueOps {

    /**
     * Appends [ref] to the session. Resolves exactly one video rather than going through
     * [Playback.resolve], whose queue loop extracts every entry eagerly and serially.
     *
     * Returns false when nothing is playing — there is no queue to append to.
     */
    suspend fun addToQueue(context: Context, ref: StreamRef): Boolean {
        val container = (context.applicationContext as LibrePipeApp).container
        val item = withContext(Dispatchers.IO) {
            val settings = container.settings.snapshot()
            val info = runCatching { Extractor.stream(ref.url) }.getOrNull()
            if (info == null || info.premiereAt != null) {
                null
            } else {
                Playback.buildItem(
                    info = info,
                    ref = ref,
                    audioOnly = settings.audioOnly || ref.isAudio,
                    maxHeight = settings.maxQuality,
                )
            }
        } ?: return false
        // A MediaController must be built and driven from the application's main thread.
        return withContext(Dispatchers.Main) {
            val controller = PlaybackOpener.connect(context)
            try {
                if (controller.playbackState == Player.STATE_IDLE || controller.currentMediaItem == null) {
                    return@withContext false
                }
                controller.addMediaItem(item)
                true
            } finally {
                controller.release()
            }
        }
    }
}
