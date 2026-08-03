package app.librepipes.data.repo

import app.librepipes.data.db.SubscriptionDao
import app.librepipes.data.db.SubscriptionEntity
import app.librepipes.data.model.ChannelRef
import kotlinx.coroutines.flow.Flow

class SubscriptionRepository(private val dao: SubscriptionDao) {

    fun observeAll(): Flow<List<SubscriptionEntity>> = dao.observeAll()

    suspend fun getAll(): List<SubscriptionEntity> = dao.getAll()

    suspend fun isSubscribed(channelUrl: String): Boolean = dao.getByUrl(channelUrl) != null

    suspend fun subscribe(channel: ChannelRef) {
        dao.upsert(
            SubscriptionEntity(
                channelUrl = channel.url,
                channelId = channel.id,
                name = channel.name,
                avatarUrl = channel.avatarUrl,
                bannerUrl = channel.bannerUrl,
                subscriberCount = channel.subscriberCount,
                description = channel.description,
                latestStreamId = null,
                lastCheckedAt = 0L,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun unsubscribe(channelUrl: String) = dao.deleteByUrl(channelUrl)

    suspend fun markChecked(channelUrl: String, latestStreamId: String?, checkedAt: Long) {
        dao.updateChecked(channelUrl, latestStreamId, checkedAt)
    }

    suspend fun markVisited(channelUrl: String, visitedAt: Long) = dao.updateVisited(channelUrl, visitedAt)

    suspend fun markAllVisited(visitedAt: Long) = dao.markAllVisited(visitedAt)

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun count(): Int = dao.count()
}
