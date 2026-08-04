package app.librepipes.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.librepipes.data.model.StreamRef
import java.util.concurrent.TimeUnit

/** Schedules/cancels one-time premiere reminders (reuses the WorkManager queue). */
object PremiereReminder {

    fun workName(videoId: String) = "premiere-$videoId"

    fun schedule(context: Context, ref: StreamRef, premiereAt: Long) {
        val delay = (premiereAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<PremiereReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    PremiereReminderWorker.KEY_REF_JSON to ref.toJson(),
                    PremiereReminderWorker.KEY_PREMIERE_AT to premiereAt,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(ref.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, videoId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(videoId))
    }
}
