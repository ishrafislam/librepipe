package app.librepipes.data.youtube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Minimal InnerTube client that talks to YouTube's private JSON API directly.
 *
 * Endpoints used (all verified against the live service):
 *  - GET  sw.js                       -> current WEB client version
 *  - GET  suggestqueries...           -> search suggestions (JSONP)
 *  - POST youtubei/v1/search          -> WEB client, optional filter params
 *  - POST youtubei/v1/browse          -> WEB client (channels, playlists, home)
 *  - POST youtubei/v1/player          -> ANDROID client (returns plain,
 *    unsigned stream URLs, so no JS signature deobfuscation is needed)
 *  - POST music.youtube.com/youtubei/v1/search -> WEB_REMIX client (Music)
 *
 * No login, no cookies, no tracking params.
 */
class InnertubeClient(private val okHttpClient: OkHttpClient) {

    /** Country code sent as `gl`; drives which popular videos the home feed returns. */
    private val region: String =
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

    /** Language sent as `hl`. */
    private val language: String =
        Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"

    companion object {
        private const val YT = "https://www.youtube.com"
        private const val YT_MUSIC = "https://music.youtube.com"
        private const val API = "https://www.youtube.com/youtubei/v1"
        private const val MUSIC_API = "https://music.youtube.com/youtubei/v1"
        private const val SW_JS_URL = "$YT/sw.js"
        private const val SUGGEST_URL = "https://suggestqueries.google.com/complete/search"

        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val ANDROID_UA =
            "com.google.android.youtube/20.05.42 (Linux; U; Android 14) gzip"

        /** Fallback so the first request still works before/without bootstrap. */
        private const val FALLBACK_WEB_VERSION = "2.20260731.00.00"
        private const val ANDROID_VERSION = "20.05.42"
        private const val REMIX_VERSION = "1.20260726.01.00"

        private val CLIENT_VERSION_RE = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"")
        private val CHANNEL_ID_RE = Regex("\"browseId\"\\s*:\\s*\"(UC[\\w-]{22})\"")
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    @Volatile
    private var cachedWebVersion: String? = null

    /** videoId -> chapters; watch pages are heavy, cache eagerly (bounded LRU). */
    private val chaptersCache = object : LinkedHashMap<String, List<Chapter>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Chapter>>) =
            size > 32
    }

    /** Lazily bootstraps the WEB client version from sw.js (cached in memory). */
    fun webClientVersion(): String {
        cachedWebVersion?.let { return it }
        val version = runCatching {
            val body = get(SW_JS_URL, WEB_UA)
            CLIENT_VERSION_RE.find(body)?.groupValues?.get(1)
        }.getOrNull()
            ?: FALLBACK_WEB_VERSION
        cachedWebVersion = version
        return version
    }

    // ------------------------------------------------------------------ API

    /** Search (WEB client). [params] is a base64 filter (videos/channels/playlists). */
    fun search(query: String, params: String?, continuation: String?): JSONObject {
        val body = webBody()
            .put("query", query)
            .apply { params?.let { put("params", it) } }
            .apply { continuation?.let { put("continuation", it) } }
        return post("$API/search", body, WEB_UA, YT)
    }

    /** Browse a channel, playlist or kiosk id (WEB client). */
    fun browse(browseId: String?, params: String?, continuation: String?): JSONObject {
        val body = webBody()
            .apply { browseId?.let { put("browseId", it) } }
            .apply { params?.let { put("params", it) } }
            .apply { continuation?.let { put("continuation", it) } }
        return post("$API/browse", body, WEB_UA, YT)
    }

    /** Full stream info for playback/download (ANDROID client, unsigned URLs). */
    fun player(videoId: String): JSONObject {
        val body = androidBody().put("videoId", videoId)
        return post("$API/player", body, ANDROID_UA, YT)
    }

    /**
     * Watch-page metadata (WEB client): channel avatar, subscriber count, upload date.
     * None of this is in the player response, and the ANDROID body returns a different
     * shape here, so this deliberately uses [webBody].
     */
    fun next(videoId: String): JSONObject {
        val body = webBody().put("videoId", videoId)
        return post("$API/next", body, WEB_UA, YT)
    }

    // ---------------------------------------------------------------- chapters

    /** Chapter markers (start times) from the watch page; empty when unavailable. */
    fun chapters(videoId: String): List<Chapter> {
        chaptersCache[videoId]?.let { return it }
        val result = runCatching {
            val html = get("$YT/watch?v=$videoId", WEB_UA)
            Parsers.extractWatchPlayerResponse(html)?.let { Parsers.parseChapters(it) }
        }.getOrNull().orEmpty()
        chaptersCache[videoId] = result
        return result
    }

    /** YouTube Music search (WEB_REMIX client). */
    fun musicSearch(query: String, continuation: String? = null): JSONObject {
        val body = remixBody()
            .put("query", query)
            .apply { continuation?.let { put("continuation", it) } }
        return post("$MUSIC_API/search", body, WEB_UA, YT_MUSIC)
    }

    // ------------------------------------------------------------- suggestions

    /** Search-as-you-type suggestions ("client=youtube&ds=yt" JSONP endpoint). */
    fun suggestions(query: String): List<String> {
        val url = "$SUGGEST_URL?client=youtube&ds=yt&q=" +
            URLEncoder.encode(query, "UTF-8")
        val raw = get(url, WEB_UA)
        val start = raw.indexOf('(')
        val end = raw.lastIndexOf(')')
        if (start < 0 || end <= start) return emptyList()
        val array = JSONArray(raw.substring(start + 1, end))
        val items = array.optJSONArray(1) ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONArray(i) ?: continue
                item.optString(0).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    /** Resolves a channel URL (@handle or /channel/...) to a channel id. */
    fun resolveChannelId(url: String): String? {
        val u = url.substringBefore("?")
        if ("/channel/" in u) {
            return u.substringAfter("/channel/").trimEnd('/')
                .takeIf { it.isNotBlank() }
        }
        val handle = u.substringAfterLast('/')
        if (!handle.startsWith("@")) return null
        return runCatching {
            val html = get("$YT/$handle", WEB_UA)
            CHANNEL_ID_RE.find(html)?.groupValues?.get(1)
        }.getOrNull()
    }

    // ------------------------------------------------------------------ http

    private fun webBody() = JSONObject()
        .put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", webClientVersion())
            .put("hl", language)
            .put("gl", region)))

    private fun androidBody() = JSONObject()
        .put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "ANDROID")
            .put("clientVersion", ANDROID_VERSION)
            .put("androidSdkVersion", 34)
            .put("hl", language)
            .put("gl", region)))

    private fun remixBody() = JSONObject()
        .put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", REMIX_VERSION)
            .put("hl", language)
            .put("gl", region)))

    private fun post(
        url: String,
        body: JSONObject,
        userAgent: String,
        origin: String,
    ): JSONObject {
        val request = Request.Builder()
            .url("$url?prettyPrint=false")
            .header("User-Agent", userAgent)
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val text = execute(request)
        return JSONObject(text)
    }

    private fun get(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/json,*/*")
            .build()
        return execute(request)
    }

    private fun execute(request: Request): String {
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${request.url}")
            }
            return text
        }
    }
}
