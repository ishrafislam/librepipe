package app.librepipes.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.librepipes.data.db.DownloadEntity
import app.librepipes.data.db.HistoryEntity
import app.librepipes.data.db.LocalPlaylistEntity
import app.librepipes.data.db.SubscriptionEntity
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import app.librepipes.di.AppContainer
import app.librepipes.util.AppError
import app.librepipes.util.Format
import app.librepipes.util.toAppError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    enum class ViewMode { LIST, GRID }

    data class ChannelItem(val subscription: SubscriptionEntity, val hasNew: Boolean)

    var channels by mutableStateOf<List<ChannelItem>>(emptyList())
        private set
    /** null = every subscription pooled together. */
    var selectedChannelUrl by mutableStateOf<String?>(null)
        private set
    var videos by mutableStateOf<List<StreamRef>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var viewMode by mutableStateOf(ViewMode.GRID)
        private set

    /**
     * Every video fetched so far across all subscriptions, newest first. YouTube has no
     * anonymous aggregate subscription feed, so this is assembled channel by channel in
     * background batches while the user reads the first page.
     */
    private val pool = mutableListOf<StreamRef>()
    private var pooledChannels = 0
    private var visibleCount = PAGE
    private var channelFeed: Extractor.ChannelFeed? = null
    private var started = false

    init {
        viewModelScope.launch {
            container.settings.viewMode.collect {
                viewMode = if (it == 0) ViewMode.LIST else ViewMode.GRID
            }
        }
        viewModelScope.launch {
            container.subscriptions.observeAll().collect { subs ->
                channels = subs.map { ChannelItem(it, hasNew = it.lastCheckedAt > it.lastVisitedAt) }
                if (subs.isEmpty()) {
                    loading = false
                } else if (!started) {
                    started = true
                    refresh()
                }
            }
        }
    }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch {
        container.settings.setViewMode(if (mode == ViewMode.LIST) 0 else 1)
    }

    /** Tap a channel to filter to it; pass null (or the same url again) to go back to all. */
    fun selectChannel(channelUrl: String?) {
        if (channelUrl == selectedChannelUrl) return
        selectedChannelUrl = channelUrl
        error = null
        if (channelUrl == null) {
            channelFeed = null
            publishPool()
            return
        }
        markChannelSeen(channelUrl)
        channelFeed = null
        videos = emptyList()
        hasMore = false
        viewModelScope.launch {
            loading = true
            try {
                val feed = Extractor.channel(channelUrl)
                feed.loadInitial()
                channelFeed = feed
                videos = feed.videos.toList()
                hasMore = feed.hasMore
            } catch (e: Exception) {
                error = e.toAppError()
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        pool.clear()
        pooledChannels = 0
        visibleCount = PAGE
        videos = emptyList()
        error = null
        selectedChannelUrl = null
        channelFeed = null
        if (channels.isEmpty()) {
            loading = false
            hasMore = false
            return
        }
        viewModelScope.launch {
            loading = true
            fetchNextBatch()
            if (pool.isEmpty()) {
                error = AppError(code = "SUBS_EMPTY", message = "Couldn't load uploads.")
            }
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        val feed = channelFeed
        viewModelScope.launch {
            loadingMore = true
            if (feed != null) {
                // Single-channel mode: real network pagination off the channel's cursor.
                val ok = runCatching { feed.loadMore() }.getOrDefault(false)
                if (ok) videos = feed.videos.toList()
                hasMore = feed.hasMore && ok
            } else {
                visibleCount += PAGE
                // Reveal from the pool, topping it up whenever we would run past the end.
                while (pool.size < visibleCount && pooledChannels < channels.size) {
                    fetchNextBatch()
                }
                publishPool()
            }
            loadingMore = false
        }
    }

    /** Fetches the next [BATCH] channels and merges everything they return into [pool]. */
    private suspend fun fetchNextBatch() {
        val batch = channels.drop(pooledChannels).take(BATCH).map { it.subscription }
        if (batch.isEmpty()) {
            hasMore = false
            return
        }
        pooledChannels += batch.size
        val fetched = coroutineScope {
            batch.map { sub ->
                async {
                    runCatching {
                        val feed = Extractor.channel(sub.channelUrl)
                        feed.loadInitial()
                        feed.videos.toList()
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().flatten()
        }
        val seen = pool.mapTo(HashSet()) { it.id }
        for (video in fetched) if (seen.add(video.id)) pool += video
        // Sort every pass: a later batch can contain videos newer than the current page.
        pool.sortByDescending { Format.approxPublishedAt(it.textualDate) ?: Long.MIN_VALUE }
        publishPool()
    }

    private fun publishPool() {
        videos = pool.take(visibleCount)
        hasMore = visibleCount < pool.size || pooledChannels < channels.size
    }

    fun markAllSeen() = viewModelScope.launch {
        container.subscriptions.markAllVisited(System.currentTimeMillis())
    }

    fun markChannelSeen(channelUrl: String) = viewModelScope.launch {
        container.subscriptions.markVisited(channelUrl, System.currentTimeMillis())
    }

    private companion object {
        const val PAGE = 20
        const val BATCH = 5
    }
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
