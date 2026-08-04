package app.librepipes.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.await
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.librepipes.data.model.StreamRef

/** Intent extra carrying the video URL the watch route should open. */
const val EXTRA_WATCH_URL = "app.librepipes.WATCH_URL"

/**
 * Hands a `StreamRef` plus its queue to the watch route. A queue is a list, so it can't
 * ride in a nav argument; this bridges the gap for the one hop between navigate and the
 * ViewModel being constructed.
 */
object WatchRequest {
    @Volatile
    private var pending: Pair<StreamRef, List<StreamRef>>? = null

    fun set(ref: StreamRef, queue: List<StreamRef>) {
        pending = ref to queue
    }

    /** Returns the pending request when it matches [url], clearing it. */
    fun take(url: String): Pair<StreamRef, List<StreamRef>>? {
        val current = pending ?: return null
        if (current.first.url != url) return null
        pending = null
        return current
    }
}

/**
 * Starts playback on the shared [PlaybackService] session and opens the
 * corresponding UI (full player, popup player, or nothing for background-only).
 */
object PlaybackOpener {

    /**
     * Opens the watch route for [ref] from outside the Compose tree (notifications,
     * the popup player, deep links). The route's ViewModel starts the session itself,
     * so this only routes.
     */
    fun openWatch(context: Context, ref: StreamRef, queue: List<StreamRef> = emptyList()) {
        WatchRequest.set(ref, queue)
        context.startActivity(
            Intent()
                .setClassName(context, "app.librepipes.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_WATCH_URL, ref.url)
        )
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
