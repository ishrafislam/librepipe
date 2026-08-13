package app.librepipes.data.youtube

import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON -> model parsing for InnerTube responses.
 * Handles both legacy renderers (videoRenderer, channelRenderer, ...) and
 * the 2025+ "lockupViewModel" layout used by channels, playlists and search.
 */
object Parsers {

    private val WATCH_PLAYER_RE = Regex(
        "(?:var\\s+ytInitialPlayerResponse|window\\[?\"ytInitialPlayerResponse\"?\\]?)\\s*=\\s*\\{"
    )

    // ------------------------------------------------------------ primitives

    /**
     * Pulls the `ytInitialPlayerResponse` JSON object out of a watch page's HTML.
     * Returns null when the page is a consent wall, logged-in shell, etc.
     */
    fun extractWatchPlayerResponse(html: String): JSONObject? {
        val match = WATCH_PLAYER_RE.find(html) ?: return null
        val text = html.substring(match.range.first)
        val open = text.indexOf('{')
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { JSONObject(text.substring(open, i + 1)) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    /**
     * Chapter markers from a watch page's `ytInitialPlayerResponse`.
     * The player API no longer ships them; they live in the watch HTML's
     * playerOverlays -> ... -> multiMarkersPlayerBarRenderer -> markersMap
     * under the DESCRIPTION_CHAPTERS (formerly CHAPTER_MARKERS) key.
     */
    fun parseChapters(root: JSONObject): List<Chapter> {
        val map = root.optJSONObject("playerOverlays")
            ?.optJSONObject("playerOverlayRenderer")
            ?.optJSONObject("decoratedPlayerBarRenderer")
            ?.optJSONObject("decoratedPlayerBarRenderer")
            ?.optJSONObject("playerBar")
            ?.optJSONObject("multiMarkersPlayerBarRenderer")
            ?.optJSONArray("markersMap")
            ?: return emptyList()
        for (i in 0 until map.length()) {
            val entry = map.optJSONObject(i) ?: continue
            if (entry.optString("key") !in setOf("DESCRIPTION_CHAPTERS", "CHAPTER_MARKERS")) continue
            val chapters = entry.optJSONObject("value")?.optJSONArray("chapters") ?: continue
            val result = buildList {
                for (j in 0 until chapters.length()) {
                    val renderer = chapters.optJSONObject(j)?.optJSONObject("chapterRenderer") ?: continue
                    val startMs = renderer.optLong("timeRangeStartMillis", -1L)
                    if (startMs < 0) continue
                    add(
                        Chapter(
                            title = runsText(renderer.optJSONObject("title")),
                            startSeconds = startMs / 1000L,
                        )
                    )
                }
            }
            return result.sortedBy { it.startSeconds }
        }
        return emptyList()
    }

    /** Extracts text from {"runs":[{"text":..}],..} / {"simpleText":..} / {"content":..}. */
    fun runsText(o: JSONObject?): String? {
        if (o == null) return null
        o.opt("content")?.let { if (it is String && it.isNotBlank()) return it }
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        if (runs.length() == 0) return null
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.takeIf { it.isNotBlank() }
    }

    /** pageHeaderViewModel title may be {"content":..} or {"dynamicTextViewModel":{"text":{"content":..}}}. */
    fun pageTitle(vm: JSONObject?): String? {
        if (vm == null) return null
        runsText(vm.optJSONObject("title"))?.let { return it }
        return runsText(
            vm.optJSONObject("title")
                ?.optJSONObject("dynamicTextViewModel")
                ?.optJSONObject("text")
        )
    }

    /** Best (largest) thumbnail URL from a {"thumbnails":[..]} / {"sources":[..]} array. */
    fun sourcesBest(o: JSONObject?): String? {
        if (o == null) return null
        val arr = o.optJSONArray("thumbnails") ?: o.optJSONArray("sources") ?: return null
        var best: String? = null
        var bestScore = -1
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            val score = t.optInt("width", 0) * t.optInt("height", 0)
            if (score > bestScore) {
                bestScore = score
                best = url
            }
        }
        // Channel avatars come back protocol-relative ("//yt3.ggpht.com/…"), which no
        // image loader can resolve. Video thumbnails already carry a scheme.
        return best?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    /** "3:34" or "1:02:34" -> seconds. */
    fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            0 -> 0L
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            else -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    }

    /** "1,799,078,920 views" -> 1799078920. */
    fun parseViewCount(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val digits = text.filter { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }

    /** "4.52M subscribers" / "12.3K views" -> number. */
    fun parseCompactCount(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val match = Regex("([\\d.,]+)\\s*([KMBkmb])?").find(text) ?: return 0L
        val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2].uppercase()) {
            "K" -> (value * 1_000).toLong()
            "M" -> (value * 1_000_000).toLong()
            "B" -> (value * 1_000_000_000).toLong()
            else -> value.toLong()
        }
    }

    // ------------------------------------------------------------- navigation

    /** First JSONObject in depth-first order whose key matches. */
    fun findFirst(root: JSONObject?, key: String): JSONObject? {
        if (root == null) return null
        root.optJSONObject(key)?.let { return it }
        for (k in root.keys()) {
            val v = root.opt(k) ?: continue
            when (v) {
                is JSONObject -> findFirst(v, key)?.let { return it }
                is JSONArray -> for (i in 0 until v.length()) {
                    v.optJSONObject(i)?.let { findFirst(it, key) }?.let { return it }
                }
            }
        }
        return null
    }

    /** All JSONObjects matching [key], depth-first. */
    fun findAll(root: JSONObject?, key: String, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        if (root == null) return out
        root.optJSONObject(key)?.let { out.add(it) }
        for (k in root.keys()) {
            val v = root.opt(k) ?: continue
            when (v) {
                is JSONObject -> findAll(v, key, out)
                is JSONArray -> for (i in 0 until v.length()) {
                    v.optJSONObject(i)?.let { findAll(it, key, out) }
                }
            }
        }
        return out
    }

    /** Pagination token from a page response (new + legacy continuation items). */
    fun continuationToken(root: JSONObject?): String? {
        if (root == null) return null
        findFirst(root, "continuationItemViewModel")?.let { return tokenIn(it) }
        findFirst(root, "continuationItemRenderer")?.let { return tokenIn(it) }
        return findFirst(root, "continuationCommand")
            ?.optString("token")?.takeIf { it.isNotBlank() }
    }

    private fun tokenIn(item: JSONObject): String? {
        item.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        item.optJSONObject("continuationCommand")
            ?.optJSONObject("innertubeCommand")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    // ------------------------------------------------------ search renderers

    fun parseVideoRenderer(o: JSONObject): StreamRef? {
        val videoId = o.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val ownerText = o.optJSONObject("ownerText")
        val ownerRun = ownerText?.optJSONArray("runs")?.optJSONObject(0)
        val isLive = isLiveRenderer(o)
        return StreamRef(
            id = videoId,
            title = runsText(o.optJSONObject("title")) ?: videoId,
            url = watchUrl(videoId),
            thumbnailUrl = sourcesBest(o.optJSONObject("thumbnail")),
            uploaderName = ownerRun?.optString("text"),
            uploaderUrl = uploaderUrlFrom(ownerRun?.optJSONObject("navigationEndpoint")),
            uploaderAvatarUrl = sourcesBest(
                o.optJSONObject("channelThumbnailSupportedRenderers")
                    ?.optJSONObject("channelThumbnailWithLinkRenderer")
                    ?.optJSONObject("thumbnail"),
            ),
            duration = if (isLive) 0L else parseDuration(runsText(o.optJSONObject("lengthText"))),
            viewCount = parseViewCount(runsText(o.optJSONObject("viewCountText"))),
            textualDate = runsText(o.optJSONObject("publishedTimeText")),
            isLive = isLive,
        )
    }

    fun parseChannelRenderer(o: JSONObject): ChannelRef? {
        val channelId = o.optString("channelId").takeIf { it.isNotBlank() } ?: return null
        return ChannelRef(
            id = channelId,
            name = runsText(o.optJSONObject("title")) ?: channelId,
            url = channelUrl(channelId),
            avatarUrl = sourcesBest(o.optJSONObject("thumbnail")),
            // YouTube swapped these two: videoCountText holds "21.1M subscribers" and
            // subscriberCountText holds "@handle". The guards keep us correct either way.
            subscriberCount = parseCompactCount(runsText(o.optJSONObject("videoCountText")))
                .takeIf { it > 0 }
                ?: parseCompactCount(runsText(o.optJSONObject("subscriberCountText"))),
            handle = runsText(o.optJSONObject("subscriberCountText"))?.takeIf { it.startsWith("@") },
            description = runsText(o.optJSONObject("descriptionSnippet")),
        )
    }

    /** Legacy search playlist renderer (rare in 2026, still supported). */
    fun parsePlaylistRenderer(o: JSONObject): PlaylistRef? {
        val playlistId = o.optString("playlistId").takeIf { it.isNotBlank() } ?: return null
        val thumb = o.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("playlistVideoThumbnailRenderer")
            ?.optJSONObject("thumbnail")
        return PlaylistRef(
            id = playlistId,
            name = runsText(o.optJSONObject("title")) ?: playlistId,
            url = playlistUrl(playlistId),
            thumbnailUrl = sourcesBest(thumb),
            uploaderName = runsText(o.optJSONObject("shortBylineText")),
            streamCount = parseViewCount(runsText(o.optJSONObject("videoCountText"))),
        )
    }

    /** New 2025+ renderer used for videos in channels, playlists and search. */
    fun parseLockupVideo(o: JSONObject): StreamRef? {
        val videoId = o.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rows = metadataRows(meta)
        val (uploader, uploaderUrl) = ownerFromRows(rows)
        val isLive = isLiveLockup(o)
        return StreamRef(
            id = videoId,
            title = runsText(meta.optJSONObject("title")) ?: videoId,
            url = watchUrl(videoId),
            thumbnailUrl = sourcesBest(
                o.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONObject("image"),
            ),
            uploaderName = uploader,
            uploaderUrl = uploaderUrl,
            // Absent on channel-tab lockups (all one channel); present on some search lockups.
            uploaderAvatarUrl = findAll(o, "avatarViewModel")
                .firstNotNullOfOrNull { sourcesBest(it.optJSONObject("image")) },
            duration = if (isLive) 0L else parseDuration(durationBadge(o)),
            viewCount = rows.firstNotNullOfOrNull {
                parseCompactCount(it).takeIf { n -> n > 0 && "view" in it }
            } ?: 0L,
            textualDate = rows.firstOrNull { isDateText(it) },
            isLive = isLive,
        )
    }

    fun parseLockupPlaylist(o: JSONObject): PlaylistRef? {
        val playlistId = o.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rows = metadataRows(meta)
        val contentImage = o.optJSONObject("contentImage")
        // A channel's playlists tab stacks the covers: the image sits one level deeper
        // and the item count only appears as an overlay badge ("5 episodes"), never in
        // the metadata rows a search result carries.
        val image = contentImage?.optJSONObject("thumbnailViewModel")?.optJSONObject("image")
            ?: contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")
                ?.optJSONObject("thumbnailViewModel")
                ?.optJSONObject("image")
        val badgeCount = contentImage
            ?.let { findAll(it, "thumbnailBadgeViewModel") }
            ?.firstNotNullOfOrNull { badge ->
                val text = badge.optString("text")
                parseViewCount(text).takeIf { it > 0 && Regex("\\d").containsMatchIn(text) }
            }
        return PlaylistRef(
            id = playlistId,
            name = runsText(meta.optJSONObject("title")) ?: playlistId,
            url = playlistUrl(playlistId),
            thumbnailUrl = sourcesBest(image),
            uploaderName = rows.firstOrNull { isOwnerText(it) },
            streamCount = rows.firstNotNullOfOrNull {
                val n = parseViewCount(it)
                if (n > 0 && Regex("\\d").containsMatchIn(it)) n else null
            } ?: badgeCount ?: 0L,
        )
    }

    /** YouTube Music list item (WEB_REMIX search results). */
    fun parseMusicItem(o: JSONObject): StreamRef? {
        val play = o.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?: return null
        val videoId = play.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() } ?: return null
        val columns = o.optJSONArray("flexColumns") ?: return null
        val title = columns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.let { runsText(it) }
            ?: videoId
        val secondary = columns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { runsText(it) }
            ?: ""
        val parts = secondary.split("•").map { it.trim() }
        val thumbnail = o.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
        return StreamRef(
            id = videoId,
            title = title,
            url = watchUrl(videoId),
            thumbnailUrl = sourcesBest(thumbnail),
            uploaderName = parts.firstOrNull { it.isNotBlank() },
            duration = parts.firstNotNullOfOrNull { parseDuration(it).takeIf { d -> d > 0 } } ?: 0L,
            viewCount = parts.firstNotNullOfOrNull {
                parseCompactCount(it).takeIf { n -> n > 0 && "view" in it }
            } ?: 0L,
        )
    }

    // -------------------------------------------------------------- channel

    /** Channel page header + metadata from a browse response (classic + new layouts). */
    fun parseChannelHeader(root: JSONObject): ChannelRef? {
        val header = root.optJSONObject("header") ?: return null
        val classic = header.optJSONObject("c4TabbedHeaderRenderer")
        val pageHeader = header.optJSONObject("pageHeaderRenderer")
        if (classic == null && pageHeader == null) return null

        val name: String
        val parts = mutableListOf<String>()
        val avatar: JSONObject?
        val banner: JSONObject?
        val description: String?

        if (classic != null) {
            name = runsText(classic.optJSONObject("title"))
                ?: classic.optString("title").takeIf { it.isNotBlank() }
                ?: return null
            runsText(classic.optJSONObject("subscriberCountText"))?.let(parts::add)
            runsText(classic.optJSONObject("videosCountText"))?.let(parts::add)
            avatar = classic.optJSONObject("avatar")
            banner = classic.optJSONObject("banner")
            description = runsText(classic.optJSONObject("description"))
        } else {
            val viewModel = pageHeader.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
            val metadataRows = viewModel?.optJSONObject("metadata")
                ?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")
                ?: return null
            for (i in 0 until metadataRows.length()) {
                val row = metadataRows.optJSONObject(i) ?: continue
                val rowParts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until rowParts.length()) {
                    rowParts.optJSONObject(j)?.optJSONObject("text")?.let { runsText(it) }?.let(parts::add)
                }
            }
            name = pageTitle(viewModel)
                ?: pageHeader.optString("pageTitle").takeIf { it.isNotBlank() }
                ?: return null
            avatar = viewModel?.optJSONObject("image")
                ?.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
            banner = viewModel?.optJSONObject("banner")
                ?.optJSONObject("imageBannerViewModel")
                ?.optJSONObject("image")
            description = runsText(
                viewModel?.optJSONObject("description")
                    ?.optJSONObject("descriptionPreviewViewModel")
                    ?.optJSONObject("description")
            )
        }

        val id = root.optJSONObject("metadata")
            ?.optJSONObject("channelMetadataRenderer")
            ?.optString("externalId")
            ?.takeIf { it.isNotBlank() }
            ?: classic?.optString("channelId")?.takeIf { it.isNotBlank() }
            ?: root.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith("UC") }
            ?: parts.firstNotNullOfOrNull { idFromText(it) }
            ?: return null
        return ChannelRef(
            id = id,
            name = name,
            url = channelUrl(id),
            avatarUrl = sourcesBest(avatar),
            bannerUrl = sourcesBest(banner),
            subscriberCount = parts.firstNotNullOfOrNull {
                parseCompactCount(it).takeIf { n -> n > 0 && "subscriber" in it }
            } ?: 0L,
            videoCount = parts.firstNotNullOfOrNull {
                parseCompactCount(it).takeIf { n -> n > 0 && "video" in it }
            } ?: 0L,
            description = description,
        )
    }

    /** Channel tabs (Videos, Shorts, Live, Playlists, ...) with their params. */
    fun channelTabs(root: JSONObject): List<Pair<String, String>> {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?: return emptyList()
        return buildList {
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val title = tab.optString("title")
                val params = tab.optJSONObject("endpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("params")
                if (title.isNotBlank() && !params.isNullOrBlank()) add(title to params)
            }
        }
    }

    // ------------------------------------------------------------- playlist

    /** Playlist header + metadata from a browse response (classic + new layouts). */
    fun parsePlaylistHeader(root: JSONObject, fallbackId: String? = null): PlaylistRef? {
        val header = root.optJSONObject("header") ?: return null
        val classic = header.optJSONObject("playlistHeaderRenderer")
        val pageHeader = header.optJSONObject("pageHeaderRenderer")
        if (classic == null && pageHeader == null) return null

        val name: String
        val parts = mutableListOf<String>()
        val thumbnail: JSONObject?

        if (classic != null) {
            name = runsText(classic.optJSONObject("title"))
                ?: classic.optString("title").takeIf { it.isNotBlank() }
                ?: fallbackId
                ?: return null
            runsText(classic.optJSONObject("ownerText"))?.let(parts::add)
            classic.optJSONArray("stats")?.let { stats ->
                for (i in 0 until stats.length()) {
                    runsText(stats.optJSONObject(i))?.let(parts::add)
                }
            }
            thumbnail = classic.optJSONObject("playlistHeaderBanner")
                ?.optJSONObject("heroPlaylistThumbnailRenderer")
                ?.optJSONObject("thumbnail")
        } else {
            val viewModel = pageHeader.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
            val metadataRows = viewModel?.optJSONObject("metadata")
                ?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")
            metadataRows?.let { rows ->
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val rowParts = row.optJSONArray("metadataParts") ?: continue
                    for (j in 0 until rowParts.length()) {
                        rowParts.optJSONObject(j)?.optJSONObject("text")?.let { runsText(it) }?.let(parts::add)
                    }
                }
            }
            name = pageTitle(viewModel)
                ?: pageHeader.optString("pageTitle").takeIf { it.isNotBlank() }
                ?: fallbackId
                ?: return null
            thumbnail = viewModel?.optJSONObject("heroImage")
                ?.optJSONObject("contentPreviewImageViewModel")
                ?.optJSONObject("image")
        }

        val id = root.optJSONObject("metadata")
            ?.optJSONObject("playlistMetadataRenderer")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }
            ?: classic?.optString("playlistId")?.takeIf { it.isNotBlank() }
            ?: findPlaylistId(root)
            ?: fallbackId
            ?: return null
        return PlaylistRef(
            id = id,
            name = name,
            url = playlistUrl(id),
            thumbnailUrl = sourcesBest(thumbnail),
            uploaderName = parts.firstOrNull { isOwnerText(it) },
            streamCount = parts.firstNotNullOfOrNull {
                val n = parseViewCount(it)
                if (n > 0 && (it.contains("video", true) || it.contains("song", true))) n else null
            } ?: 0L,
        )
    }

    private fun findPlaylistId(root: JSONObject): String? {
        val id = findFirstString(root, "playlistId") ?: return null
        return id.takeIf { it.startsWith("PL") || it.startsWith("VL") }
    }

    /** First String value found in depth-first order (e.g. "playlistId": "PL.."). */
    private fun findFirstString(root: JSONObject?, key: String): String? {
        if (root == null) return null
        (root.opt(key) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        for (k in root.keys()) {
            val v = root.opt(k) ?: continue
            when (v) {
                is JSONObject -> findFirstString(v, key)?.let { return it }
                is JSONArray -> for (i in 0 until v.length()) {
                    v.optJSONObject(i)?.let { findFirstString(it, key) }?.let { return it }
                }
            }
        }
        return null
    }

    // --------------------------------------------------------------- player

    /** Player response -> full [StreamInfo] for playback & downloads. */
    fun parseStreamInfo(root: JSONObject, videoId: String): StreamInfo {
        val statusObj = root.optJSONObject("playabilityStatus")
        val details = root.optJSONObject("videoDetails") ?: JSONObject()
        val isUpcoming = details.optBoolean("isUpcoming")
        val status = statusObj?.optString("status") ?: "ERROR"
        if (status != "OK") {
            val reason = runsText(statusObj?.optJSONObject("reason"))
                ?: runsText(statusObj?.optJSONObject("errorScreen")
                    ?.optJSONObject("playerErrorMessageRenderer")
                    ?.optJSONObject("subreason"))
                ?: statusObj?.optString("reason")
                ?: "Video unavailable"
            // An upcoming premiere/live simply isn't live yet — surface it as a
            // Countdown instead of an error.
            if (!(status == "LIVE_STREAM_OFFLINE" && isUpcoming)) {
                val errorCode = statusObj?.optString("errorCode")
                throw PlayabilityException(
                    status = status,
                    kind = PlayabilityException.classify(status, reason, errorCode),
                    errorCode = errorCode,
                    message = reason,
                )
            }
        }
        val id = details.optString("videoId").ifBlank { videoId }
        // `isLiveContent` is set on anything that was ever streamed live, finished VODs
        // included, so it cannot stand in for "live right now" — using it stripped the
        // seek bar, subtitles and quality menu from every past live stream.
        val isLive = details.optBoolean("isLive")
        val streaming = root.optJSONObject("streamingData")

        val progressive = mutableListOf<StreamFormat>()
        val videoOnly = mutableListOf<StreamFormat>()
        val audioOnly = mutableListOf<StreamFormat>()
        streaming?.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) {
                parseFormat(arr.optJSONObject(i))?.let { progressive += it }
            }
        }
        streaming?.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = parseFormat(arr.optJSONObject(i)) ?: continue
                when {
                    f.hasVideo && f.hasAudio -> progressive += f
                    f.hasVideo -> videoOnly += f
                    else -> audioOnly += f
                }
            }
        }

        val captions = root.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
            ?: JSONArray()
        val subtitles = buildList {
            for (i in 0 until captions.length()) {
                val t = captions.optJSONObject(i) ?: continue
                val base = t.optString("baseUrl").takeIf { it.isNotBlank() } ?: continue
                val kind = t.optString("kind")
                add(
                    SubtitleTrack(
                        url = toVttUrl(base),
                        languageTag = t.optString("languageCode").ifBlank { null },
                        name = t.optJSONObject("name")?.let { runsText(it) },
                        isAutoGenerated = kind == "asr" || kind == "ocr",
                    ),
                )
            }
        }

        val streamType = when {
            isLive -> StreamType.LIVE
            progressive.isEmpty() && audioOnly.isNotEmpty() && videoOnly.isEmpty() -> StreamType.AUDIO
            else -> StreamType.NORMAL
        }
        val premiereAt = if (isUpcoming) {
            (details.optString("startTimestamp").toLongOrNull() ?: 0L).takeIf { it > 0 }?.let { it * 1000 }
        } else null
        return StreamInfo(
            id = id,
            title = details.optString("title").ifBlank { id },
            url = watchUrl(id),
            thumbnailUrl = sourcesBest(details.optJSONObject("thumbnail")),
            uploaderName = details.optString("author").ifBlank { null },
            uploaderUrl = details.optString("channelId").takeIf { it.isNotBlank() }?.let { channelUrl(it) },
            duration = details.optString("lengthSeconds").toLongOrNull() ?: 0L,
            viewCount = details.optString("viewCount").toLongOrNull() ?: 0L,
            description = details.optString("shortDescription").ifBlank { null },
            streamType = streamType,
            premiereAt = premiereAt,
            videoStreams = progressive,
            videoOnlyStreams = videoOnly,
            audioStreams = audioOnly,
            dashMpdUrl = streaming?.optString("dashManifestUrl").orEmpty().ifBlank { null },
            hlsUrl = streaming?.optString("hlsManifestUrl").orEmpty().ifBlank { null },
            subtitles = subtitles,
        )
    }

    /** Channel avatar, subscriber line and upload date from a `next` response. */
    fun parseWatchNext(root: JSONObject): WatchNext {
        val owner = findAll(root, "videoOwnerRenderer").firstOrNull()
        val primary = findAll(root, "videoPrimaryInfoRenderer").firstOrNull()
        val ownerRun = owner?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
        val channelId = owner?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() }
        return WatchNext(
            uploaderName = ownerRun?.optString("text")?.takeIf { it.isNotBlank() },
            uploaderUrl = channelId?.let { channelUrl(it) }
                ?: uploaderUrlFrom(ownerRun?.optJSONObject("navigationEndpoint")),
            uploaderId = channelId,
            uploaderAvatarUrl = sourcesBest(owner?.optJSONObject("thumbnail")),
            subscriberText = runsText(owner?.optJSONObject("subscriberCountText")),
            dateText = runsText(primary?.optJSONObject("dateText")),
            relativeDateText = runsText(primary?.optJSONObject("relativeDateText")),
        )
    }

    private fun parseFormat(o: JSONObject?): StreamFormat? {
        if (o == null) return null
        // All ANDROID client formats come pre-signed as plain URLs.
        val url = o.optString("url").takeIf { it.isNotBlank() } ?: return null
        val mime = o.optString("mimeType").ifBlank { "video/mp4" }
        val codecs = mime.substringAfter("codecs=\"").substringBefore("\"")
            .split(',').map { it.trim().lowercase() }
        val track = o.optJSONObject("audioTrack")
        return StreamFormat(
            url = url,
            itag = o.optInt("itag"),
            mimeType = mime,
            suffix = suffixOf(mime, codecs),
            bitrate = o.optInt("bitrate"),
            width = o.optInt("width"),
            height = o.optInt("height"),
            audioQuality = o.optString("audioQuality").ifBlank { null },
            approxDurationMs = o.optString("approxDurationMs").ifBlank { null },
            codecs = mime.substringAfter("codecs=\"", "").substringBefore("\"").ifBlank { null },
            contentLength = o.optString("contentLength").toLongOrNull() ?: 0L,
            fps = o.optInt("fps"),
            audioSampleRate = o.optString("audioSampleRate").toIntOrNull() ?: 0,
            audioChannels = o.optInt("audioChannels"),
            initRange = byteRange(o.optJSONObject("initRange")),
            indexRange = byteRange(o.optJSONObject("indexRange")),
            audioTrackId = track?.optString("id")?.takeIf { it.isNotBlank() },
            audioTrackName = track?.optString("displayName")?.takeIf { it.isNotBlank() },
            audioIsDefault = track?.optBoolean("audioIsDefault") ?: false,
            // On dubbed videos audioLanguage is absent and the tag lives in the track
            // id's prefix: "bn.3" -> "bn", "zh-Hans.3" -> "zh-Hans".
            audioLanguage = o.optString("audioLanguage").takeIf { it.isNotBlank() }
                ?: track?.optString("id")?.substringBefore('.')?.takeIf { it.isNotBlank() },
            hasVideo = codecs.any { c -> c.isCodec(VIDEO_CODECS) },
            hasAudio = codecs.any { c -> c.isCodec(AUDIO_CODECS) },
        )
    }

    /** `{"start":"0","end":"740"}` -> `"0-740"`; null when either bound is missing. */
    private fun byteRange(o: JSONObject?): String? {
        val start = o?.optString("start")?.takeIf { it.isNotBlank() } ?: return null
        val end = o.optString("end").takeIf { it.isNotBlank() } ?: return null
        return "$start-$end"
    }

    /** "avc1.42001e" matches codec family "avc1" (dotted profile suffixes stripped). */
    private fun String.isCodec(family: String): Boolean =
        substringBefore('.').let { base -> family.split(',').any { it == base } }

    private fun suffixOf(mime: String, codecs: List<String>): String {
        val container = mime.substringBefore(';').trim()
        if (container.startsWith("audio/")) {
            return when {
                codecs.any { it == "opus" } -> "opus"
                container == "audio/mp4" -> "m4a"
                container == "audio/mpeg" -> "mp3"
                else -> "webm"
            }
        }
        return if (container == "video/mp4") "mp4" else "webm"
    }

    /** Subtitle URLs carry fmt=srv3; rewrite to plain VTT (confirmed to work). */
    private fun toVttUrl(url: String): String {
        val withVtt = Regex("(fmt=|format=)srv3").replace(url, "\$1vtt")
        return if (withVtt != url) withVtt else "$url&fmt=vtt"
    }

    // -------------------------------------------------------------- helpers

    private const val VIDEO_CODECS =
        "avc1,avc3,vp9,vp09,av01,av1,hvc1,hev1,mp4v,hevc,avc"
    private const val AUDIO_CODECS =
        "mp4a,opus,vorbis,ac-3,ec-3,mp3,flac,m4a,amr,aac"

    private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
    private fun channelUrl(channelId: String) = "https://www.youtube.com/channel/$channelId"
    private fun playlistUrl(playlistId: String) =
        "https://www.youtube.com/playlist?list=$playlistId"

    private fun isLiveRenderer(o: JSONObject): Boolean {
        if (o.optBoolean("isLive")) return true
        val badges = o.optJSONArray("badges") ?: return false
        for (i in 0 until badges.length()) {
            val label = badges.optJSONObject(i)
                ?.optJSONObject("metadataBadgeRenderer")
                ?.optString("label")
            if (label?.contains("LIVE", ignoreCase = true) == true) return true
        }
        return false
    }

    private fun uploaderUrlFrom(endpoint: JSONObject?): String? {
        val url = endpoint?.optJSONObject("commandMetadata")
            ?.optJSONObject("webCommandMetadata")
            ?.optString("url")
            ?: return null
        if (url.startsWith("/")) return "https://www.youtube.com$url"
        return url
    }

    private fun metadataRows(meta: JSONObject): List<String> {
        val rows = meta.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
            ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val parts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) {
                    parts.optJSONObject(j)?.optJSONObject("text")?.let { runsText(it) }?.let(::add)
                }
            }
        }
    }

    private fun ownerFromRows(rows: List<String>): Pair<String?, String?> {
        val owner = rows.firstOrNull { row ->
            !isDateText(row) &&
                !Regex("\\d").containsMatchIn(row) &&
                row.length in 1..60
        }
        return owner to null
    }

    private fun durationBadge(o: JSONObject): String? {
        val overlays = o.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONArray("overlays")
            ?: return null
        for (i in 0 until overlays.length()) {
            val badges = overlays.optJSONObject(i)
                ?.optJSONObject("thumbnailBottomOverlayViewModel")
                ?.optJSONArray("badges")
                ?: continue
            for (j in 0 until badges.length()) {
                val text = badges.optJSONObject(j)
                    ?.optJSONObject("thumbnailBadgeViewModel")
                    ?.optString("text")
                    ?.takeIf { it.contains(':') }
                    ?: continue
                return text
            }
        }
        return null
    }

    /**
     * Live detection for [parseLockupVideo]. The LIVE badge sits under a different
     * overlay wrapper than the duration badge, so scan every badge view model under
     * the thumbnail rather than a single fixed path.
     */
    private fun isLiveLockup(o: JSONObject): Boolean {
        val image = o.optJSONObject("contentImage") ?: return false
        for (badge in findAll(image, "thumbnailBadgeViewModel")) {
            if (badge.optString("text").equals("LIVE", ignoreCase = true)) return true
            if (badge.optString("badgeStyle").contains("LIVE", ignoreCase = true)) return true
        }
        return false
    }

    private fun isDateText(text: String): Boolean =
        Regex("(ago|day|week|month|year|hour|minute)", RegexOption.IGNORE_CASE).containsMatchIn(text)

    private fun isOwnerText(text: String): Boolean =
        text.startsWith("@") || text.length < 60 && !Regex("\\d").containsMatchIn(text)

    private fun idFromText(text: String): String? =
        Regex("UC[\\w-]{22}").find(text)?.value
}
