package app.librepipes.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.librepipes.data.db.DownloadEntity
import app.librepipes.data.db.GroupEntity
import app.librepipes.data.db.HistoryEntity
import app.librepipes.data.db.LocalPlaylistEntity
import app.librepipes.data.db.SubscriptionEntity
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import app.librepipes.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ------------------------------------------------------------------- Library

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    var playlists by mutableStateOf<List<LocalPlaylistEntity>>(emptyList())
        private set
    var itemCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set

    init {
        viewModelScope.launch {
            container.playlists.observePlaylists().collect { playlists = it }
        }
        viewModelScope.launch {
            container.playlists.observeCounts().collect { counts ->
                itemCounts = counts.associate { it.playlistId to it.count }
            }
        }
    }

    fun createPlaylist(name: String) = viewModelScope.launch { container.playlists.create(name) }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { container.playlists.rename(id, name) }

    fun deletePlaylist(id: Long) = viewModelScope.launch { container.playlists.delete(id) }
}

// -------------------------------------------------------------------- History

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    data class HistoryEntry(val ref: StreamRef?, val entity: HistoryEntity)

    var entries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set
    var recording by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            container.history.observeRecent(100).collect { list ->
                entries = list.map { HistoryEntry(StreamRef.fromJson(it.streamJson), it) }
            }
        }
        viewModelScope.launch {
            container.settings.recordHistory.collect { recording = it }
        }
    }

    fun setRecording(value: Boolean) = viewModelScope.launch { container.settings.setRecordHistory(value) }

    fun clear() = viewModelScope.launch { container.history.clear() }

    fun remove(streamId: String) = viewModelScope.launch { container.history.deleteByStreamId(streamId) }
}

// ------------------------------------------------------------------ Downloads

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    data class DownloadEntry(val ref: StreamRef?, val entity: DownloadEntity)

    var downloads by mutableStateOf<List<DownloadEntry>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            container.downloads.observeAll().collect { list ->
                downloads = list.map { DownloadEntry(StreamRef.fromJson(it.streamJson), it) }
            }
        }
    }

    fun delete(id: Long) = viewModelScope.launch { container.downloadManager.remove(id) }

    fun cancel(id: Long) {
        container.downloadManager.cancel(id)
        viewModelScope.launch { container.downloads.updateState(id, DownloadState.CANCELLED) }
    }

    fun retry(id: Long) = viewModelScope.launch { container.downloadManager.retry(id) }

    fun clear() = viewModelScope.launch {
        container.downloads.observeAll().collect { list ->
            list.forEach { container.downloadManager.remove(it.id) }
        }
    }
}

// ------------------------------------------------------------- Subscriptions

class SubscriptionsViewModel(private val container: AppContainer) : ViewModel() {

    data class ChannelItem(val subscription: SubscriptionEntity, val groupIds: List<Long>)
    data class UploadItem(val ref: StreamRef, val channel: SubscriptionEntity, val isNew: Boolean)

    var channels by mutableStateOf<List<ChannelItem>>(emptyList())
        private set
    var groups by mutableStateOf<List<GroupEntity>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var selectedGroupId by mutableStateOf<Long?>(null)
    var uploads by mutableStateOf<List<UploadItem>>(emptyList())
        private set
    var uploadsLoading by mutableStateOf(false)
        private set

    private var feedsFetched = false

    init {
        viewModelScope.launch {
            combine(
                container.subscriptions.observeAll(),
                container.groups.observeGroups(),
                container.groups.observeChannelRefs(),
            ) { subs, gs, refs -> Triple(subs, gs, refs) }
                .collect { (subs, gs, refs) ->
                    channels = subs.map { sub ->
                        ChannelItem(
                            sub,
                            refs.filter { it.channelUrl == sub.channelUrl }.map { it.groupId },
                        )
                    }
                    groups = gs
                    loading = false
                    if (subs.isNotEmpty() && !feedsFetched) {
                        feedsFetched = true
                        refreshUploads()
                    }
                }
        }
    }

    fun selectGroup(groupId: Long?) {
        selectedGroupId = groupId
    }

    fun refreshUploads() {
        val subs = channels.map { it.subscription }
        if (subs.isEmpty()) {
            uploads = emptyList()
            return
        }
        viewModelScope.launch {
            uploadsLoading = true
            val items = coroutineScope {
                subs.take(20).map { sub ->
                    async {
                        runCatching {
                            val feed = Extractor.channel(sub.channelUrl)
                            feed.loadInitial()
                            feed.videos.firstOrNull()?.let { first ->
                                UploadItem(
                                    ref = first,
                                    channel = sub,
                                    isNew = sub.lastCheckedAt > sub.lastVisitedAt && first.id == sub.latestStreamId,
                                )
                            }
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            uploads = items
            uploadsLoading = false
        }
    }

    fun markAllSeen() = viewModelScope.launch {
        container.subscriptions.markAllVisited(System.currentTimeMillis())
        uploads = uploads.map { it.copy(isNew = false) }
    }

    fun markChannelSeen(channelUrl: String) = viewModelScope.launch {
        container.subscriptions.markVisited(channelUrl, System.currentTimeMillis())
        uploads = uploads.map {
            if (it.channel.channelUrl == channelUrl) it.copy(isNew = false) else it
        }
    }

    fun createGroup(name: String) = viewModelScope.launch { container.groups.createGroup(name) }

    fun renameGroup(id: Long, name: String) = viewModelScope.launch { container.groups.renameGroup(id, name) }

    fun deleteGroup(id: Long) = viewModelScope.launch {
        container.groups.deleteGroup(id)
        if (selectedGroupId == id) selectedGroupId = null
    }

    fun assignChannel(channelUrl: String, groupId: Long?) =
        viewModelScope.launch { container.groups.assignChannel(channelUrl, groupId) }
}

// --------------------------------------------------------- Local playlist

class LocalPlaylistViewModel(
    private val container: AppContainer,
    val playlistId: Long,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var items by mutableStateOf<List<app.librepipes.data.db.LocalPlaylistItemEntity>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            container.playlists.observePlaylists().collect { list ->
                name = list.find { it.id == playlistId }?.name ?: ""
            }
        }
        viewModelScope.launch {
            container.playlists.observeItems(playlistId).collect { items = it }
        }
    }

    fun rename(newName: String) = viewModelScope.launch { container.playlists.rename(playlistId, newName) }

    fun delete() = viewModelScope.launch { container.playlists.delete(playlistId) }

    fun removeItem(itemId: Long) = viewModelScope.launch { container.playlists.removeItem(itemId) }
}

// ------------------------------------------------------------------ Settings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings

    fun setTheme(value: Int) = viewModelScope.launch { settings.setTheme(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settings.setDynamicColor(value) }
    fun setMaxQuality(value: Int) = viewModelScope.launch { settings.setMaxQuality(value) }
    fun setAudioOnly(value: Boolean) = viewModelScope.launch { settings.setAudioOnly(value) }
    fun setCaptionsEnabled(value: Boolean) = viewModelScope.launch { settings.setCaptionsEnabled(value) }
    fun setRecordHistory(value: Boolean) = viewModelScope.launch { settings.setRecordHistory(value) }
    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch {
        settings.setNotificationsEnabled(value)
        app.librepipes.notify.UploadScheduler.reschedule(container.appContext, settings)
    }
    fun setRefreshInterval(value: Int) = viewModelScope.launch {
        settings.setRefreshInterval(value)
        app.librepipes.notify.UploadScheduler.reschedule(container.appContext, settings)
    }
    fun setDownloadQuality(value: Int) = viewModelScope.launch { settings.setDownloadQuality(value) }

    fun clearHistory() = viewModelScope.launch { container.history.clear() }
    fun clearDownloads() = viewModelScope.launch {
        container.downloads.observeAll().collect { list ->
            list.forEach { container.downloadManager.remove(it.id) }
        }
    }
}
