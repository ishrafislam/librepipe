package app.librepipes.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.di.AppContainer
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

    data class HomeSection(val channel: ChannelRef, val videos: List<StreamRef>)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val sections: List<HomeSection> = emptyList(),
        val trending: List<StreamRef> = emptyList(),
        val hasSubscriptions: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val subs = container.subscriptions.getAll()
                if (subs.isEmpty()) {
                    val trending = runCatching { Extractor.trending() }.getOrDefault(emptyList())
                    _uiState.update {
                        it.copy(loading = false, hasSubscriptions = false, sections = emptyList(), trending = trending)
                    }
                } else {
                    val sections = coroutineScope {
                        subs.take(12).map { sub ->
                            async {
                                runCatching {
                                    val feed = Extractor.channel(sub.channelUrl)
                                    feed.loadInitial()
                                    HomeSection(feed.channel, feed.videos.take(6))
                                }.getOrNull()
                            }
                        }.awaitAll().filterNotNull()
                    }
                    _uiState.update {
                        it.copy(loading = false, hasSubscriptions = true, sections = sections, trending = emptyList())
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Could not load the feed") }
            }
        }
    }
}

// -------------------------------------------------------------------- Search

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var items by mutableStateOf<List<Extractor.SearchItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var activeFilter by mutableStateOf(Extractor.SearchFilter.ALL)
        private set
    var searched by mutableStateOf(false)
        private set

    private var feed: Extractor.SearchFeed? = null
    private var suggestionJob: kotlinx.coroutines.Job? = null

    fun onQueryChange(newQuery: String) {
        query = newQuery
        searched = false
        suggestionJob?.cancel()
        if (newQuery.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { Extractor.suggestions(newQuery) }.getOrDefault(emptyList())
        }
    }

    fun search(filter: Extractor.SearchFilter = activeFilter) {
        val q = query.trim()
        if (q.isBlank()) return
        activeFilter = filter
        suggestions = emptyList()
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val f = Extractor.search(q, filter)
                f.loadInitial()
                feed = f
                items = f.items.toList()
                hasMore = f.hasMore
                searched = true
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
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
    var error by mutableStateOf<String?>(null)
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
                error = e.message ?: "Could not load channel"
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
    var error by mutableStateOf<String?>(null)
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
                error = e.message ?: "Could not load playlist"
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
