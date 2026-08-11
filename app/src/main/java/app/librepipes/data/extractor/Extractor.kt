package app.librepipes.data.extractor

import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.data.youtube.InnertubeClient
import app.librepipes.data.youtube.Parsers
import app.librepipes.data.youtube.StreamInfo
import app.librepipes.data.youtube.Chapter
import app.librepipes.data.youtube.WatchNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException

/**
 * Thin suspend-function facade over the native InnerTube client.
 * Talks to YouTube's JSON API directly (no NewPipe Extractor).
 *
 * Note: since YouTube retired the global "Trending" kiosk in 2025,
 * [trending] now serves the "What to watch" home feed, which needs a
 * signed-in account to be populated (anonymous clients get an empty list).
 */
object Extractor {

    @Volatile
    private var client: InnertubeClient? = null

    /** Must be called once at startup with the app's shared OkHttp client. */
    fun init(okHttpClient: OkHttpClient) {
        client = InnertubeClient(okHttpClient)
    }

    private suspend fun <T> onIo(block: (InnertubeClient) -> T): T {
        val c = client ?: throw IllegalStateException("Extractor not initialized")
        return withContext(Dispatchers.IO) { block(c) }
    }

    // ------------------------------------------------------------------ Search

    enum class SearchFilter(val filterId: String?, internal val params: String?) {
        ALL(null, null),
        VIDEOS("videos", "EgIQAQ%3D%3D"),
        CHANNELS("channels", "EgIQAg%3D%3D"),
        PLAYLISTS("playlists", "EgIQAw%3D%3D"),
    }

    sealed interface SearchItem {
        data class Video(val stream: StreamRef) : SearchItem
        data class Channel(val channel: ChannelRef) : SearchItem
        data class Playlist(val playlist: PlaylistRef) : SearchItem

        fun key(): String = when (this) {
            is Video -> "v-${stream.id}"
            is Channel -> "c-${channel.id}"
            is Playlist -> "p-${playlist.id}"
        }
    }

    class SearchFeed internal constructor(
        private val client: InnertubeClient,
        private val query: String,
        private val filter: SearchFilter,
        initialItems: List<SearchItem>,
        private var nextToken: String?,
    ) {
        val items = mutableListOf<SearchItem>()
        var hasMore = true
            private set

        init {
            val seen = HashSet<String>()
            for (item in initialItems) {
                if (seen.add(item.key())) items += item
            }
        }

        /** Kept for API compatibility; the initial page loads eagerly in [Extractor.search]. */
        suspend fun loadInitial() = Unit

        suspend fun loadMore(): Boolean = withContext(Dispatchers.IO) {
            val token = nextToken ?: return@withContext false
            consume(client.search(query, filter.params, token))
            true
        }

        private fun consume(page: JSONObject) {
            val parsed = parseSearchPage(page)
            val seen = items.mapTo(HashSet()) { it.key() }
            for (item in parsed.items) {
                if (seen.add(item.key())) items += item
            }
            nextToken = parsed.nextToken
            if (nextToken == null) hasMore = false
        }
    }

    suspend fun search(query: String, filter: SearchFilter): SearchFeed = onIo { c ->
        val page = c.search(query, filter.params, null)
        val parsed = parseSearchPage(page)
        SearchFeed(c, query, filter, parsed.items, parsed.nextToken)
    }

    private data class ParsedSearch(val items: List<SearchItem>, val nextToken: String?)

    private fun parseSearchPage(page: JSONObject): ParsedSearch {
        val items = mutableListOf<SearchItem>()
        for (r in Parsers.findAll(page, "videoRenderer")) {
            Parsers.parseVideoRenderer(r)?.let { items += SearchItem.Video(it) }
        }
        for (r in Parsers.findAll(page, "channelRenderer")) {
            Parsers.parseChannelRenderer(r)?.let { items += SearchItem.Channel(it) }
        }
        for (r in Parsers.findAll(page, "playlistRenderer")) {
            Parsers.parsePlaylistRenderer(r)?.let { items += SearchItem.Playlist(it) }
        }
        for (r in Parsers.findAll(page, "lockupViewModel")) {
            when (r.optString("contentType")) {
                "LOCKUP_CONTENT_TYPE_VIDEO" -> Parsers.parseLockupVideo(r)?.let { items += SearchItem.Video(it) }
                "LOCKUP_CONTENT_TYPE_PLAYLIST" -> Parsers.parseLockupPlaylist(r)?.let { items += SearchItem.Playlist(it) }
            }
        }
        for (r in Parsers.findAll(page, "musicResponsiveListItemRenderer")) {
            Parsers.parseMusicItem(r)?.let { items += SearchItem.Video(it) }
        }
        return ParsedSearch(items, Parsers.continuationToken(page))
    }

    suspend fun suggestions(query: String): List<String> = onIo { c ->
        runCatching { c.suggestions(query) }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ Streams

    /** Full stream info needed for playback & downloads. */
    suspend fun stream(url: String): StreamInfo = onIo { c ->
        val id = idFromUrl(url)
        val page = c.player(id)
        Parsers.parseStreamInfo(page, id)
    }

    /** Chapter markers (start times) for a video; empty when unavailable. */
    suspend fun chapters(url: String): List<Chapter> = onIo { c ->
        c.chapters(idFromUrl(url))
    }

    /**
     * Watch-page metadata (channel avatar, subscriber line, upload date). Kept apart
     * from [stream] on purpose — playback must not wait on a metadata request.
     */
    suspend fun watchNext(url: String): WatchNext = onIo { c ->
        Parsers.parseWatchNext(c.next(idFromUrl(url)))
    }

    // ------------------------------------------------------------------ Channels

    class ChannelFeed internal constructor(
        private val client: InnertubeClient,
        private var nextToken: String?,
        val channel: ChannelRef,
        initialVideos: List<StreamRef>,
    ) {
        val videos = mutableListOf<StreamRef>()
        var hasMore = true
            private set

        init {
            val seen = HashSet<String>()
            for (video in initialVideos) {
                if (seen.add(video.id)) videos += video.withChannel()
            }
        }

        /**
         * A channel page omits the owner from every item — it is implicit there — so the
         * parsed refs carry no uploader name, url or avatar. Stamp them from the header
         * once, here, and every consumer of a channel feed gets complete refs.
         */
        private fun StreamRef.withChannel(): StreamRef = copy(
            uploaderName = uploaderName?.takeIf { it.isNotBlank() } ?: channel.name,
            uploaderUrl = uploaderUrl?.takeIf { it.isNotBlank() } ?: channel.url,
            uploaderAvatarUrl = uploaderAvatarUrl ?: channel.avatarUrl,
        )

        /** Kept for API compatibility; initial page loads eagerly in [Extractor.channel]. */
        suspend fun loadInitial() = Unit

        suspend fun loadMore(): Boolean = withContext(Dispatchers.IO) {
            val token = nextToken ?: return@withContext false
            val page = client.browse(null, null, token)
            consume(page)
            true
        }

        private fun consume(page: JSONObject) {
            val seen = videos.mapTo(HashSet()) { it.id }
            for (r in Parsers.findAll(page, "lockupViewModel")) {
                if (r.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                    Parsers.parseLockupVideo(r)?.let { v ->
                        if (seen.add(v.id)) videos += v.withChannel()
                    }
                }
            }
            nextToken = Parsers.continuationToken(page)
            if (nextToken == null) hasMore = false
        }
    }

    suspend fun channel(url: String): ChannelFeed = onIo { c ->
        val browseId = c.resolveChannelId(url)
            ?: throw IOException("Could not resolve channel URL")
        val page = c.browse(browseId, null, null)
        val channel = Parsers.parseChannelHeader(page)
            ?: throw IOException("Not a channel page")
        val tabs = Parsers.channelTabs(page)
        val videosTab = tabs.firstOrNull { it.first.contains("video", ignoreCase = true) }
            ?: tabs.firstOrNull()
        val videosPage = if (videosTab != null) c.browse(browseId, videosTab.second, null) else null
        val videos = mutableListOf<StreamRef>()
        if (videosPage != null) {
            for (r in Parsers.findAll(videosPage, "lockupViewModel")) {
                if (r.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                    Parsers.parseLockupVideo(r)?.let { videos += it }
                }
            }
        }
        ChannelFeed(c, videosPage?.let { Parsers.continuationToken(it) }, channel, videos)
    }

    // ------------------------------------------------------------------ Playlists

    class PlaylistFeed internal constructor(
        private val client: InnertubeClient,
        private var nextToken: String?,
        val playlist: PlaylistRef,
        initialVideos: List<StreamRef>,
    ) {
        val videos = mutableListOf<StreamRef>()
        var hasMore = true
            private set

        init {
            val seen = HashSet<String>()
            for (video in initialVideos) {
                if (seen.add(video.id)) videos += video
            }
        }

        /** Kept for API compatibility; initial page loads eagerly in [Extractor.playlist]. */
        suspend fun loadInitial() = Unit

        suspend fun loadMore(): Boolean = withContext(Dispatchers.IO) {
            val token = nextToken ?: return@withContext false
            val page = client.browse(null, null, token)
            consume(page)
            true
        }

        private fun consume(page: JSONObject) {
            val seen = videos.mapTo(HashSet()) { it.id }
            for (r in Parsers.findAll(page, "lockupViewModel")) {
                if (r.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                    Parsers.parseLockupVideo(r)?.let { v -> if (seen.add(v.id)) videos += v }
                }
            }
            nextToken = Parsers.continuationToken(page)
            if (nextToken == null) hasMore = false
        }
    }

    suspend fun playlist(url: String): PlaylistFeed = onIo { c ->
        val rawId = idFromUrl(url).removePrefix("VL")
        val playlistId = if (rawId.startsWith("PL")) rawId else "PL$rawId"
        val page = c.browse("VL$playlistId", null, null)
        val playlist = Parsers.parsePlaylistHeader(page, fallbackId = playlistId)
            ?: throw IOException("Not a playlist page")
        val videos = mutableListOf<StreamRef>()
        for (r in Parsers.findAll(page, "lockupViewModel")) {
            if (r.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                Parsers.parseLockupVideo(r)?.let { videos += it }
            }
        }
        PlaylistFeed(c, Parsers.continuationToken(page), playlist, videos)
    }

    // ------------------------------------------------------------------ Trending

    /**
     * YouTube removed the global trending feed; this now returns the
     * "What to watch" home feed (empty for anonymous clients).
     */
    suspend fun trending(): List<StreamRef> = onIo { c ->
        val page = c.browse("FEwhat_to_watch", null, null)
        val items = mutableListOf<StreamRef>()
        for (r in Parsers.findAll(page, "videoRenderer")) {
            Parsers.parseVideoRenderer(r)?.let { items += it }
        }
        for (r in Parsers.findAll(page, "lockupViewModel")) {
            if (r.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                Parsers.parseLockupVideo(r)?.let { items += it }
            }
        }
        items
    }
}
