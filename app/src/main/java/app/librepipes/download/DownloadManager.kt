package app.librepipes.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import app.librepipes.data.prefs.SettingsRepository
import app.librepipes.data.repo.DownloadRepository

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settings: SettingsRepository,
) {

    suspend fun enqueue(ref: StreamRef, mode: DownloadMode) {
        val id = repository.insert(ref, mode)
        val input = Data.Builder()
            .putLong(DownloadWorker.KEY_DOWNLOAD_ID, id)
            .putString(DownloadWorker.KEY_MODE, mode.name)
            .putString(DownloadWorker.KEY_REF, ref.toJson())
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download-$id",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("download-$id")
    }

    suspend fun remove(id: Long) {
        cancel(id)
        repository.delete(id)
    }

    suspend fun retry(id: Long) {
        val entity = repository.getById(id) ?: return
        val ref = StreamRef.fromJson(entity.streamJson) ?: return
        val mode = runCatching { DownloadMode.valueOf(entity.mode) }.getOrDefault(DownloadMode.VIDEO)
        repository.update(id, state = DownloadState.QUEUED, progress = 0)
        enqueue(ref, mode)
    }
}
