package app.librepipes.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String?,
    val latestStreamId: String?,
    val lastCheckedAt: Long,
    val addedAt: Long,
    val lastVisitedAt: Long = 0L,
)

/** Locally stored search queries — never leave the device. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val createdAt: Long,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

@Entity(tableName = "group_channels", primaryKeys = ["groupId", "channelUrl"])
data class GroupChannelCrossRef(
    val groupId: Long,
    val channelUrl: String,
)

@Entity(
    tableName = "history",
    indices = [Index(value = ["streamId"], unique = true)],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamId: String,
    val streamJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
)

@Entity(tableName = "playlists")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_items")
data class LocalPlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val streamJson: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamJson: String,
    val mode: String,
    val state: String,
    val progress: Int,
    val totalBytes: Long,
    val fileUri: String?,
    val error: String?,
    val createdAt: Long,
)
