package app.librepipes.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.librepipes.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object UploadScheduler {

    private const val WORK_NAME = "subscription-upload-check"

    /** (Re)schedules the periodic worker according to the user's settings. */
    fun reschedule(context: Context, settings: SettingsRepository) {
        val workManager = WorkManager.getInstance(context)
        val snapshot = runBlocking { settings.snapshot() }
        workManager.cancelUniqueWork(WORK_NAME)
        if (!snapshot.notificationsEnabled) return

        val intervalHours = snapshot.refreshIntervalHours.coerceAtLeast(1).toLong()
        val request = PeriodicWorkRequestBuilder<UploadRefreshWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
