package app.librepipes.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.await
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.librepipes.data.model.StreamRef

/**
 * Starts playback on the shared [PlaybackService] session and opens the
 * corresponding UI (full player, popup player, or nothing for background-only).
 */
object PlaybackOpener {

    /**
     * Resolves and starts playback for [ref] (optionally inside [queue]),
     * then opens the full-screen player.
     */
    suspend fun playFull(context: Context, ref: StreamRef, queue: List<StreamRef> = listOf(ref)) {
        startSession(context, ref, queue)
        val intent = Intent(context, NowPlayingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(NowPlayingActivity.EXTRA_STREAM_JSON, ref.toJson())
            .putStringArrayListExtra(
                NowPlayingActivity.EXTRA_QUEUE_JSON,
                ArrayList(queue.map { it.toJson() })
            )
        context.startActivity(intent)
    }

    /** Starts playback without opening any UI (background/audio-only mode). */
    suspend fun playBackground(context: Context, ref: StreamRef, queue: List<StreamRef> = listOf(ref)) {
        startSession(context, ref, queue)
    }

    /** Ensures [ref] is playing on the session without switching UI. */
    suspend fun startSession(context: Context, ref: StreamRef, queue: List<StreamRef> = listOf(ref)) {
        val resolved = Playback.resolve(context, ref, queue)
        val controller = connect(context)
        try {
            val current = controller.currentMediaItem?.mediaId
            if (current == ref.id && controller.playbackState == androidx.media3.common.Player.STATE_READY) {
                controller.play()
                return
            }
            controller.setMediaItems(resolved.items, resolved.startIndex, resolved.startPosition)
            controller.prepare()
            controller.play()
        } finally {
            controller.release()
        }
    }

    /** Plays a local file (e.g. a finished download) on the session. */
    suspend fun playUri(context: Context, uri: android.net.Uri, title: String) {
        val controller = connect(context)
        try {
            val item = androidx.media3.common.MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        } finally {
            controller.release()
        }
    }

    suspend fun connect(context: Context): MediaController {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        return MediaController.Builder(context, token).buildAsync().await()
    }
}
