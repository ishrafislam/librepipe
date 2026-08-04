package app.librepipes.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.librepipes.data.db.SearchHistoryEntity
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.di.AppContainer
import app.librepipes.util.AppError
import app.librepipes.util.toAppError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- Home

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: AppError? = null,
        /** Subscription uploads merged with region-popular videos, deduped. */
        val feed: List<StreamRef> = emptyList(),
        val hasSubscriptions: Boolean = false,
        val inProgressIds: Set<String> = emptySet(),
        val downloadedIds: Set<String> = emptySet(),
        val progressById: Map<String, Float> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.history.observeRecent(100).collect { history ->
                val unfinished = history.filter { entry ->
                    entry.durationMs > 0 && entry.positionMs > 0 &&
                        entry.durationMs - entry.positionMs > 15_000
                }
                val inProgressIds = unfinished.map { it.streamId }.toSet()
                val progressById = unfinished.associate { entry ->
                    entry.streamId to (entry.positionMs.toFloat() / entry.durationMs.coerceAtLeast(1L))
                }
                _uiState.update { it.copy(inProgressIds = inProgressIds, progressById = progressById) }
            }
        }
        viewModelScope.launch {
            container.downloads.observeAll().collect { downloads ->
                val ids = downloads
                    .filter { it.state == DownloadState.DONE.name }
                    .mapNotNull { StreamRef.fromJson(it.streamJson)?.id }
                    .toSet()
                _uiState.update { it.copy(downloadedIds = ids) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val subs = container.subscriptions.getAll()
                val feed = coroutineScope {
                    val popular = async {
                        runCatching { Extractor.trending() }.getOrDefault(emptyList())
                    }
                    val channels = subs.take(12).map { sub ->
                        async {
                            runCatching {
                                val channel = Extractor.channel(sub.channelUrl)
                                channel.loadInitial()
                                // Channel-tab items carry no avatar of their own, so take it
                                // from the header (falling back to the stored subscription).
                                val avatar = channel.channel.avatarUrl ?: sub.avatarUrl
                                channel.videos.take(6).map {
                                    it.copy(uploaderAvatarUrl = it.uploaderAvatarUrl ?: avatar)
                                }
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                    (interleave(channels) + popular.await()).distinctBy { it.id }
                }
                _uiState.update {
                    it.copy(loading = false, hasSubscriptions = subs.isNotEmpty(), feed = feed)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.toAppError()) }
            }
        }
    }

    /**
     * Round-robin flatten: one video per channel, then the next from each. A plain
     * flatten would stack the first channel's whole upload run at the top of the feed.
     */
    private fun interleave(lists: List<List<StreamRef>>): List<StreamRef> {
        if (lists.isEmpty()) return emptyList()
        val out = ArrayList<StreamRef>(lists.sumOf { it.size })
        val longest = lists.maxOf { it.size }
        for (i in 0 until longest) {
            for (list in lists) list.getOrNull(i)?.let { out += it }
        }
        return out
    }
}

// -------------------------------------------------------------------- Search

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    /** Which of the three views the screen shows. Explicit, never inferred from the data. */
    enum class Mode { RECENTS, SUGGESTIONS, RESULTS }

    var query by mutableStateOf("")
        private set
    var mode by mutableStateOf(Mode.RECENTS)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var items by mutableStateOf<List<Extractor.SearchItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var recents by mutableStateOf<List<SearchHistoryEntity>>(emptyList())
        private set
    var subscribedUrls by mutableStateOf<Set<String>>(emptySet())
        private set

    private var feed: Extractor.SearchFeed? = null
    private var suggestionJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            container.searchHistory.observeRecent(12).collect { recents = it }
        }
        viewModelScope.launch {
            container.subscriptions.observeAll().collect { subs ->
                subscribedUrls = subs.map { it.channelUrl }.toSet()
            }
        }
    }

    fun toggleSubscribe(channel: ChannelRef) {
        viewModelScope.launch {
            if (channel.url in subscribedUrls) {
                container.subscriptions.unsubscribe(channel.url)
            } else {
                container.subscriptions.subscribe(channel)
            }
        }
    }

    fun removeRecent(id: Long) {
        viewModelScope.launch { container.searchHistory.remove(id) }
    }

    fun clearRecents() {
        viewModelScope.launch { container.searchHistory.clear() }
    }

    /** Typing only ever moves between RECENTS and SUGGESTIONS — never into results. */
    fun onQueryChange(newQuery: String) {
        query = newQuery
        suggestionJob?.cancel()
        if (newQuery.isBlank()) {
            mode = Mode.RECENTS
            suggestions = emptyList()
            return
        }
        mode = Mode.SUGGESTIONS
        suggestionJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { Extractor.suggestions(newQuery) }.getOrDefault(emptyList())
        }
    }

    /** Tapping a suggestion or a recent: search it directly, with no debounce job spawned. */
    fun onSuggestionClick(suggestion: String) = search(suggestion)

    fun search(newQuery: String = query) {
        val q = newQuery.trim()
        if (q.isBlank()) return
        // Cancel first, or an in-flight suggestions fetch lands after the results.
        suggestionJob?.cancel()
        query = q
        suggestions = emptyList()
        mode = Mode.RESULTS
        viewModelScope.launch {
            container.searchHistory.add(q)
            loading = true
            error = null
            try {
                val f = Extractor.search(q, Extractor.SearchFilter.ALL)
                f.loadInitial()
                feed = f
                items = f.items.toList()
                hasMore = f.hasMore
            } catch (e: Exception) {
                error = e.toAppError()
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val f = feed ?: return
        if (!f.hasMore || loading) return
        viewModelScope.launch {
            loading = true
            val ok = runCatching { f.loadMore() }.getOrDefault(false)
            if (ok) {
                items = f.items.toList()
                hasMore = f.hasMore
            }
            loading = false
        }
    }
}

// ------------------------------------------------------------------ Channel

class ChannelViewModel(
    private val container: AppContainer,
    private val channelUrl: String,
) : ViewModel() {

    var channel by mutableStateOf<ChannelRef?>(null)
        private set
    var videos by mutableStateOf<List<StreamRef>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set
    var subscribed by mutableStateOf(false)
        private set

    private var feed: Extractor.ChannelFeed? = null

    init {
        viewModelScope.launch {
            container.subscriptions.observeAll().collect { subs ->
                subscribed = subs.any { it.channelUrl == channelUrl }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val f = Extractor.channel(channelUrl)
                f.loadInitial()
                feed = f
                channel = f.channel
                videos = f.videos.toList()
            } catch (e: Exception) {
                error = e.toAppError()
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val f = feed ?: return
        if (!f.hasMore || loadingMore) return
        viewModelScope.launch {
            loadingMore = true
            val ok = runCatching { f.loadMore() }.getOrDefault(false)
            if (ok) videos = f.videos.toList()
            loadingMore = false
        }
    }

    fun toggleSubscribe() {
        viewModelScope.launch {
            val ch = channel ?: return@launch
            if (subscribed) {
                container.subscriptions.unsubscribe(ch.url)
            } else {
                container.subscriptions.subscribe(ch)
            }
        }
    }
}

// ---------------------------------------------------------------- Playlist

class PlaylistViewModel(
    private val container: AppContainer,
    private val playlistUrl: String,
) : ViewModel() {

    var playlist by mutableStateOf<PlaylistRef?>(null)
        private set
    var videos by mutableStateOf<List<StreamRef>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<AppError?>(null)
        private set

    private var feed: Extractor.PlaylistFeed? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val f = Extractor.playlist(playlistUrl)
                f.loadInitial()
                feed = f
                playlist = f.playlist
                videos = f.videos.toList()
            } catch (e: Exception) {
                error = e.toAppError()
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val f = feed ?: return
        if (!f.hasMore || loadingMore) return
        viewModelScope.launch {
            loadingMore = true
            val ok = runCatching { f.loadMore() }.getOrDefault(false)
            if (ok) videos = f.videos.toList()
            loadingMore = false
        }
    }
}
