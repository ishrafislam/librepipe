package app.librepipes.data.repo

import app.librepipes.data.db.HistoryDao
import app.librepipes.data.db.HistoryEntity
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    suspend fun getByStreamId(streamId: String): HistoryEntity? = dao.getByStreamId(streamId)

    suspend fun record(ref: StreamRef, positionMs: Long, durationMs: Long, watchedAt: Long = System.currentTimeMillis()) {
        dao.upsert(
            HistoryEntity(
                streamId = ref.id,
                streamJson = ref.toJson(),
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = watchedAt,
            )
        )
    }

    suspend fun updatePosition(streamId: String, positionMs: Long, durationMs: Long, watchedAt: Long) {
        dao.updatePosition(streamId, positionMs, durationMs, watchedAt)
    }

    suspend fun deleteByStreamId(streamId: String) = dao.deleteByStreamId(streamId)

    suspend fun clear() = dao.clear()
}
