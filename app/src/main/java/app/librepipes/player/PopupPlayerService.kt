package app.librepipes.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import app.librepipes.R
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Floating popup player. Renders the shared [PlaybackService] session inside a
 * draggable, resizable overlay window. Requires the "display over other apps"
 * permission, which is requested before this service is started.
 */
class PopupPlayerService : android.app.Service() {

    companion object {
        const val EXTRA_REF_JSON = "ref_json"
        const val EXTRA_QUEUE_JSON = "queue_json"
        const val ACTION_STOP = "action_stop"
        private const val NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var stopRequested = false

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var startX = 0
    private var startY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPopup(pause = true)
            return START_NOT_STICKY
        }
        if (overlayView == null && !stopRequested) {
            startForeground(NOTIFICATION_ID, buildNotification())
            createOverlay(intent)
        }
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "popup"
        val stopIntent = android.app.PendingIntent.getService(
            this,
            0,
            Intent(this, PopupPlayerService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Popup player active")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)
            .build()
    }

    private fun createOverlay(intent: Intent?) {
        val ref = StreamRef.fromJson(intent?.getStringExtra(EXTRA_REF_JSON))
        val queue = intent?.getStringArrayListExtra(EXTRA_QUEUE_JSON)
            ?.mapNotNull { StreamRef.fromJson(it) } ?: emptyList()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.popup_player, null) as FrameLayout
        overlayView = view

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        layoutParams = WindowManager.LayoutParams(
            (420 * density).toInt(),
            (236 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (96 * density).toInt()
        }
        wm.addView(view, layoutParams)

        attachHandlers(view)
        connectController(view, ref, queue)
    }

    @OptIn(UnstableApi::class)
    private fun connectController(view: FrameLayout, ref: StreamRef?, queue: List<StreamRef>) {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = future.get()
                this.controller = connected
                val playerView = view.findViewById<PlayerView>(R.id.popup_player_view)
                playerView.player = connected
                val playPause = view.findViewById<ImageButton>(R.id.btn_play_pause)
                connected.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPause.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }
                })
                if (ref != null) {
                    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                        PlaybackOpener.startSession(this@PopupPlayerService, ref, queue)
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun attachHandlers(view: FrameLayout) {
        val playPause = view.findViewById<ImageButton>(R.id.btn_play_pause)
        val close = view.findViewById<ImageButton>(R.id.btn_close)
        val expand = view.findViewById<ImageButton>(R.id.btn_expand)

        playPause.setOnClickListener { controller?.playOrPause() }
        close.setOnClickListener { stopPopup(pause = true) }
        expand.setOnClickListener {
            val current = controller?.currentMediaItem
            if (current != null) {
                val refJson = current.mediaMetadata.extras?.getString(Playback.EXTRA_REF_JSON)
                val intent = Intent(this, NowPlayingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (refJson != null) intent.putExtra(NowPlayingActivity.EXTRA_STREAM_JSON, refJson)
                startActivity(intent)
            }
            stopPopup(pause = false)
        }

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val p = layoutParams ?: return true
                    val density = resources.displayMetrics.density
                    val minW = (160 * density).toInt()
                    val minH = (90 * density).toInt()
                    val maxW = (720 * density).toInt()
                    val maxH = (420 * density).toInt()
                    val newW = (p.width * detector.scaleFactor).toInt().coerceIn(minW, maxW)
                    val newH = (newW * 236 / 420).toInt().coerceIn(minH, maxH)
                    p.width = newW
                    p.height = newH
                    windowManager?.updateViewLayout(view, p)
                    return true
                }
            },
        )

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    startX = layoutParams?.x ?: 0
                    startY = layoutParams?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    scaleDetector.onTouchEvent(event)
                    val p = layoutParams ?: return@setOnTouchListener true
                    p.x = (startX + (event.rawX - dragStartX)).toInt()
                    p.y = (startY + (event.rawY - dragStartY)).toInt()
                    windowManager?.updateViewLayout(v, p)
                    true
                }
                else -> false
            }
        }
    }

    private fun stopPopup(pause: Boolean) {
        if (stopRequested) return
        stopRequested = true
        if (pause) controller?.pause()
        controller = null
        // releaseFuture releases the controller returned by the future — the single
        // authoritative release point (avoid double-releasing the controller).
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPopup(pause = true)
        super.onDestroy()
    }
}
