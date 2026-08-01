package app.librepipes.data.repo

import app.librepipes.data.db.DownloadDao
import app.librepipes.data.db.DownloadEntity
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val dao: DownloadDao) {

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun insert(ref: StreamRef, mode: DownloadMode): Long =
        dao.insert(
            DownloadEntity(
                streamJson = ref.toJson(),
                mode = mode.name,
                state = DownloadState.QUEUED.name,
                progress = 0,
                totalBytes = 0L,
                fileUri = null,
                error = null,
                createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun update(id: Long, state: DownloadState, progress: Int, error: String? = null, fileUri: String? = null, totalBytes: Long = 0L) {
        dao.update(id, state.name, progress, error, fileUri, totalBytes)
    }

    suspend fun updateState(id: Long, state: DownloadState, error: String? = null) {
        dao.updateState(id, state.name, error)
    }

    suspend fun updateProgress(id: Long, progress: Int) = dao.updateProgress(id, progress)

    suspend fun delete(id: Long) = dao.delete(id)
}
