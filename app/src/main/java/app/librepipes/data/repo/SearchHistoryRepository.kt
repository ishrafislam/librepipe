package app.librepipes.data.repo

import app.librepipes.data.db.SearchHistoryDao
import app.librepipes.data.db.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepository(private val dao: SearchHistoryDao) {

    fun observeRecent(limit: Int = 12): Flow<List<SearchHistoryEntity>> = dao.observeRecent(limit)

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dao.insert(SearchHistoryEntity(query = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}
