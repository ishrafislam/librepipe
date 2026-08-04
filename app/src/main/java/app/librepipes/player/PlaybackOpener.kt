package app.librepipes.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.await
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.librepipes.data.model.StreamRef
import app.librepipes.util.toAppError

/**
 * Starts playback on the shared [PlaybackService] session and opens the
 * corresponding UI (full player, popup player, or nothing for background-only).
 */
object PlaybackOpener {

    /**
     * Resolves and starts playback for [ref] (optionally inside [queue]),
     * then opens the full-screen player. Never crashes: a resolve failure is
     * forwarded to [NowPlayingActivity] as an in-frame error state.
     */
    suspend fun playFull(context: Context, ref: StreamRef, queue: List<StreamRef> = listOf(ref)) {
        val result = runCatching { startSession(context, ref, queue) }
        val error = result.exceptionOrNull()?.toAppError()
        val premiereAt = result.getOrNull()?.premiereAt
        val intent = Intent(context, NowPlayingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(NowPlayingActivity.EXTRA_STREAM_JSON, ref.toJson())
            .putStringArrayListExtra(
                NowPlayingActivity.EXTRA_QUEUE_JSON,
                ArrayList(queue.map { it.toJson() })
            )
        if (error != null) {
            intent.putExtra(NowPlayingActivity.EXTRA_STREAM_ERROR_CODE, error.code)
                .putExtra(NowPlayingActivity.EXTRA_STREAM_ERROR_MESSAGE, error.message)
        }
        if (premiereAt != null) {
            intent.putExtra(NowPlayingActivity.EXTRA_PREMIERE_AT, premiereAt)
        }
        context.startActivity(intent)
    }

    /** Starts playback without opening any UI (background/audio-only mode). */
    suspend fun playBackground(context: Context, ref: StreamRef, queue: List<StreamRef> = listOf(ref)) {
        runCatching { startSession(context, ref, queue) }
    }

    /**
     * Ensures [ref] is playing on the session without switching UI. Returns
     * the resolved items (null on failure — callers decide how to surface it).
     */
    suspend fun startSession(
        context: Context,
        ref: StreamRef,
        queue: List<StreamRef> = listOf(ref),
    ): Playback.Resolved? {
        val resolved = Playback.resolve(context, ref, queue)
        val controller = connect(context)
        try {
            val current = controller.currentMediaItem?.mediaId
            if (current == ref.id && controller.playbackState == androidx.media3.common.Player.STATE_READY) {
                controller.play()
                return resolved
            }
            controller.setMediaItems(resolved.items, resolved.startIndex, resolved.startPosition)
            controller.prepare()
            controller.play()
            return resolved
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
