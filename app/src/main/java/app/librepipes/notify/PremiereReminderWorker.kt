package app.librepipes.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.librepipes.data.model.StreamRef
import app.librepipes.player.NowPlayingActivity

/**
 * Fires the one-time "Remind me" notification for a premiere at its scheduled
 * start time (board 05). Scheduled per video with a unique work name so
 * re-tapping "Remind me" simply replaces the previous reminder.
 */
class PremiereReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val refJson = inputData.getString(KEY_REF_JSON) ?: return Result.failure()
        val premiereAt = inputData.getLong(KEY_PREMIERE_AT, 0L)
        val ref = StreamRef.fromJson(refJson) ?: return Result.failure()
        if (premiereAt <= 0L) return Result.failure()

        // Ran early (device was off, delayed queue, …) — hold off until the
        // premiere is actually starting.
        val remaining = premiereAt - System.currentTimeMillis()
        if (remaining > 60_000) return Result.retry()

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return Result.success()

        val intent = Intent(applicationContext, NowPlayingActivity::class.java)
            .putExtra(NowPlayingActivity.EXTRA_STREAM_JSON, ref.toJson())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ref.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = ref.title.ifBlank { "A premiere is starting" }
        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.PREMIERE,
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("This premiere is going live now")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(ref.id.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_REF_JSON = "ref_json"
        const val KEY_PREMIERE_AT = "premiere_at"
    }
}
