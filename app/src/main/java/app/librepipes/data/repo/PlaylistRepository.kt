package app.librepipes.data.repo

import app.librepipes.data.db.LocalPlaylistEntity
import app.librepipes.data.db.LocalPlaylistItemEntity
import app.librepipes.data.db.PlaylistDao
import app.librepipes.data.db.PlaylistItemDao
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val itemDao: PlaylistItemDao,
) {

    fun observePlaylists(): Flow<List<LocalPlaylistEntity>> = playlistDao.observeAll()

    fun observeCounts(): Flow<List<PlaylistItemDao.PlaylistCount>> = itemDao.observeCounts()

    suspend fun create(name: String): Long =
        playlistDao.insert(LocalPlaylistEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = playlistDao.rename(id, name)

    suspend fun delete(id: Long) {
        itemDao.deleteFor(id)
        playlistDao.delete(id)
    }

    fun observeItems(playlistId: Long): Flow<List<LocalPlaylistItemEntity>> = itemDao.observeFor(playlistId)

    suspend fun getItems(playlistId: Long): List<LocalPlaylistItemEntity> = itemDao.getFor(playlistId)

    suspend fun addItem(playlistId: Long, ref: StreamRef) {
        val position = itemDao.maxPosition(playlistId) + 1
        itemDao.insert(
            LocalPlaylistItemEntity(
                playlistId = playlistId,
                streamJson = ref.toJson(),
                position = position,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Appends to the implicit "Watch later" list, creating it on first use. It is an
     * ordinary local playlist, so it shows up in Library with the rest.
     */
    suspend fun addToWatchLater(ref: StreamRef) {
        val id = playlistDao.findByName(WATCH_LATER)?.id ?: create(WATCH_LATER)
        addItem(id, ref)
    }

    companion object {
        const val WATCH_LATER = "Watch later"
    }

    suspend fun removeItem(itemId: Long) = itemDao.delete(itemId)

    suspend fun itemsAsRefs(playlistId: Long): List<StreamRef> =
        itemDao.getFor(playlistId).mapNotNull { StreamRef.fromJson(it.streamJson) }
}
