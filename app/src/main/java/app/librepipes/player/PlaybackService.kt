package app.librepipes.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.librepipes.LibrePipeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the ExoPlayer instance. Playback keeps running when the app UI is
 * closed (background audio) and the system media notification is shown.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val app = application as LibrePipeApp
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(
                    DefaultDataSource.Factory(
                        this,
                        OkHttpDataSource.Factory(app.container.okHttpClient)
                    )
                )
            )
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        // Autoplay off means "stop at the end of this item" rather than advancing through
        // the queue. The setting has existed in DataStore since before there was a queue
        // to apply it to, and nothing read it.
        scope.launch {
            app.container.settings.autoplay.collect { on ->
                player.pauseAtEndOfMediaItems = !on
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Do nothing: audio keeps playing in the background.
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
