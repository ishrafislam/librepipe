package app.librepipes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY addedAt DESC")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE channelUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelUrl = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE subscriptions SET latestStreamId = :streamId, lastCheckedAt = :checkedAt WHERE channelUrl = :url")
    suspend fun updateChecked(url: String, streamId: String?, checkedAt: Long)

    @Query("UPDATE subscriptions SET lastVisitedAt = :visitedAt WHERE channelUrl = :url")
    suspend fun updateVisited(url: String, visitedAt: Long)

    @Query("UPDATE subscriptions SET lastVisitedAt = :visitedAt WHERE lastVisitedAt < :visitedAt")
    suspend fun markAllVisited(visitedAt: Long)

    @Query("SELECT COUNT(*) FROM subscriptions WHERE lastCheckedAt > lastVisitedAt")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun count(): Int
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY position ASC, name ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY position ASC, name ASC")
    suspend fun getAll(): List<GroupEntity>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @androidx.room.Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM groups")
    suspend fun maxPosition(): Int
}

@Dao
interface GroupChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: GroupChannelCrossRef)

    @Query("DELETE FROM group_channels WHERE channelUrl = :channelUrl")
    suspend fun deleteForChannel(channelUrl: String)

    @Query("DELETE FROM group_channels WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: Long)

    @Query("SELECT groupId FROM group_channels WHERE channelUrl = :channelUrl")
    suspend fun groupIdsForChannel(channelUrl: String): List<Long>

    @Query("SELECT * FROM group_channels")
    fun observeAll(): Flow<List<GroupChannelCrossRef>>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history WHERE streamId = :streamId LIMIT 1")
    suspend fun getByStreamId(streamId: String): HistoryEntity?

    @Query("UPDATE history SET positionMs = :positionMs, durationMs = :durationMs, watchedAt = :watchedAt WHERE streamId = :streamId")
    suspend fun updatePosition(streamId: String, positionMs: Long, durationMs: Long, watchedAt: Long)

    @Query("DELETE FROM history WHERE streamId = :streamId")
    suspend fun deleteByStreamId(streamId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): LocalPlaylistEntity?

    @Insert
    suspend fun insert(playlist: LocalPlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlaylistItemDao {
    data class PlaylistCount(val playlistId: Long, val count: Int)

    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_items GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistCount>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeFor(playlistId: Long): Flow<List<LocalPlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getFor(playlistId: Long): List<LocalPlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LocalPlaylistItemEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteFor(playlistId: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("UPDATE downloads SET state = :state, progress = :progress, error = :error, fileUri = :fileUri, totalBytes = :totalBytes WHERE id = :id")
    suspend fun update(id: Long, state: String, progress: Int, error: String?, fileUri: String?, totalBytes: Long)

    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id")
    suspend fun updateState(id: Long, state: String, error: String?)

    @Query("UPDATE downloads SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
