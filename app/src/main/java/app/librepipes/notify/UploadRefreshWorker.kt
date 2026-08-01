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
import kotlinx.coroutines.flow.first
import app.librepipes.LibrePipeApp
import app.librepipes.data.db.SubscriptionEntity
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.StreamRef
import app.librepipes.player.NowPlayingActivity

/**
 * Periodically checks subscribed channels and posts a grouped notification
 * for new uploads. Runs entirely in the background (no Google services).
 */
class UploadRefreshWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LibrePipeApp
        val container = app.container
        val subscriptions = container.subscriptions.getAll()
        if (subscriptions.isEmpty()) return Result.success()

        var failedChannels = 0
        for (subscription in subscriptions.take(40)) {
            try {
                val feed = Extractor.channel(subscription.channelUrl)
                feed.loadInitial()
                val videos = feed.videos
                if (videos.isEmpty()) continue

                val latest = videos.first().id
                val newVideos = findNewVideos(videos, subscription.latestStreamId)
                if (newVideos.isNotEmpty() && subscription.latestStreamId != null) {
                    postUploadNotifications(subscription, newVideos)
                }
                container.subscriptions.markChecked(
                    subscription.channelUrl,
                    latest,
                    System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                failedChannels++
            }
        }
        return if (failedChannels > 0) Result.retry()
        else Result.success()
    }

    private fun findNewVideos(videos: List<StreamRef>, lastSeen: String?): List<StreamRef> {
        if (lastSeen == null) return emptyList() // first check: just record the baseline
        val index = videos.indexOfFirst { it.id == lastSeen }
        return if (index >= 0) {
            videos.take(index).take(10)
        } else {
            // Baseline id not found on the page anymore — assume the freshest are new.
            videos.take(3)
        }
    }

    private suspend fun postUploadNotifications(subscription: SubscriptionEntity, videos: List<StreamRef>) {
        val app = applicationContext as LibrePipeApp
        val enabled = runCatching { app.container.settings.notificationsEnabled.first() }.getOrDefault(true)
        if (!enabled) return

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val manager = NotificationManagerCompat.from(applicationContext)
        for (video in videos) {
            val intent = Intent(applicationContext, NowPlayingActivity::class.java)
                .putExtra(NowPlayingActivity.EXTRA_STREAM_JSON, video.toJson())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                video.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(
                applicationContext,
                NotificationChannels.UPLOADS,
            )
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(video.title)
                .setContentText("New upload from ${subscription.name}")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(subscription.channelId)
                .build()
            manager.notify(video.id.hashCode(), notification)
        }

        val summary = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.UPLOADS,
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("${videos.size} new ${if (videos.size == 1) "video" else "videos"}")
            .setContentText("from ${subscription.name}")
            .setGroup(subscription.channelId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(subscription.channelId.hashCode(), summary)
    }

}

