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
     * Ensures [ref] is playing on the session without switching UI. Returns the resolved
     * items, or null when the session already held [ref] and nothing had to be resolved
     * (callers refetch their own metadata in that case).
     */
    suspend fun startSession(
        context: Context,
        ref: StreamRef,
        queue: List<StreamRef> = listOf(ref),
    ): Playback.Resolved? {
        // Already somewhere in the timeline: seek to it instead of rebuilding. A rebuild
        // would throw away a queue the user built, and reopening the playing video from
        // the mini player is the most common way to land here — often mid-buffer, so this
        // cannot be narrowed to STATE_READY.
        if (adoptExisting(context, ref)) return null

        val resolved = Playback.resolve(context, ref, queue)
        val controller = connect(context)
        try {
            // Disable the renderers first so the video codec is released. Swapping items
            // on a playing player let it carry a codec configured for the previous stream
            // into the next one — visible as torn macroblocks for the first seconds when
            // moving from a DASH video to a live HLS stream.
            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(resolved.items, resolved.startIndex, resolved.startPosition)
            controller.prepare()
            controller.play()
            return resolved
        } finally {
            controller.release()
        }
    }

    private suspend fun adoptExisting(context: Context, ref: StreamRef): Boolean {
        val controller = connect(context)
        try {
            if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) return false
            val index = (0 until controller.mediaItemCount)
                .firstOrNull { controller.getMediaItemAt(it).mediaId == ref.id }
                ?: return false
            if (index != controller.currentMediaItemIndex) controller.seekTo(index, 0L)
            controller.play()
            return true
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
