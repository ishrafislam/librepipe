package app.librepipes.data.repo

import app.librepipes.data.db.GroupChannelDao
import app.librepipes.data.db.GroupChannelCrossRef
import app.librepipes.data.db.GroupDao
import app.librepipes.data.db.GroupEntity
import app.librepipes.data.db.SubscriptionDao
import kotlinx.coroutines.flow.Flow

class GroupRepository(
    private val groupDao: GroupDao,
    private val refDao: GroupChannelDao,
    private val subscriptionDao: SubscriptionDao,
) {

    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeAll()

    fun observeChannelRefs(): Flow<List<GroupChannelCrossRef>> = refDao.observeAll()

    suspend fun createGroup(name: String): Long {
        val position = groupDao.maxPosition() + 1
        return groupDao.insert(GroupEntity(name = name, position = position))
    }

    suspend fun renameGroup(id: Long, name: String) {
        groupDao.getAll().find { it.id == id }?.let { groupDao.update(it.copy(name = name)) }
    }

    suspend fun deleteGroup(id: Long) {
        refDao.deleteForGroup(id)
        groupDao.delete(id)
    }

    /** Assigns a channel to exactly one group (or to none when groupId is null). */
    suspend fun assignChannel(channelUrl: String, groupId: Long?) {
        refDao.deleteForChannel(channelUrl)
        if (groupId != null) {
            refDao.insert(GroupChannelCrossRef(groupId = groupId, channelUrl = channelUrl))
        }
    }

    suspend fun groupIdsForChannel(channelUrl: String): List<Long> = refDao.groupIdsForChannel(channelUrl)

    suspend fun count(): Int = groupDao.getAll().size
}
