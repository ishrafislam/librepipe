package app.librepipes.player

import android.content.Context
import android.content.Intent
import app.librepipes.data.model.StreamRef

/**
 * Starts [PopupPlayerService], first bouncing the user through the overlay-permission
 * screen when needed and replaying the request once they come back.
 */
object PopupLauncher {
    private var pendingRef: StreamRef? = null
    private var pendingQueue: List<StreamRef> = emptyList()

    fun requestAndStart(context: Context, ref: StreamRef, queue: List<StreamRef>) {
        if (android.provider.Settings.canDrawOverlays(context)) {
            context.startService(
                Intent(context, PopupPlayerService::class.java)
                    .putExtra(PopupPlayerService.EXTRA_REF_JSON, ref.toJson())
                    .putStringArrayListExtra(
                        PopupPlayerService.EXTRA_QUEUE_JSON,
                        ArrayList(queue.map { it.toJson() })
                    )
            )
        } else {
            pendingRef = ref
            pendingQueue = queue
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun consumePending(context: Context): Boolean {
        val ref = pendingRef ?: return false
        pendingRef = null
        if (android.provider.Settings.canDrawOverlays(context)) {
            requestAndStart(context, ref, pendingQueue)
            pendingQueue = emptyList()
            return true
        }
        return false
    }
}
