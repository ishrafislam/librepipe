package app.librepipes.data.youtube

import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parsers.kt tests against live InnerTube responses captured in
 * app/src/test/resources/fixtures/ (see AGENTS.md "If extraction breaks").
 * Expected values were read from the fixtures at capture time (2026-08-02).
 */
class ParsersTest {

    private fun fixture(name: String): JSONObject =
        JSONObject(File("src/test/resources/fixtures/$name.json").readText())

    private fun findAllByKey(root: JSONObject, key: String): List<JSONObject> =
        Parsers.findAll(root, key)

    // ------------------------------------------------------------ primitives

    @Test
    fun runsText_simpleTextContentAndRuns() {
        assertEquals("hi", Parsers.runsText(JSONObject("""{"simpleText":"hi"}""")))
        assertEquals("ab", Parsers.runsText(JSONObject("""{"runs":[{"text":"a"},{"text":"b"}]}""")))
        assertEquals("x", Parsers.runsText(JSONObject("""{"content":"x"}""")))
        assertNull(Parsers.runsText(JSONObject("""{"dynamicTextViewModel":{"text":{"content":"y"}}}""")))
        assertNull(Parsers.runsText(null))
        assertNull(Parsers.runsText(JSONObject("""{"runs":[]}""")))
    }

    @Test
    fun pageTitle_unwrapsDynamicTextViewModel() {
        val vm = JSONObject("""{"title":{"dynamicTextViewModel":{"text":{"content":"Kotlin by JetBrains"}}}}""")
        assertEquals("Kotlin by JetBrains", Parsers.pageTitle(vm))
        assertEquals("plain", Parsers.pageTitle(JSONObject("""{"title":{"content":"plain"}}""")))
        assertNull(Parsers.pageTitle(null))
    }

    @Test
    fun parseDuration_formats() {
        assertEquals(142L, Parsers.parseDuration("2:22"))
        assertEquals(3754L, Parsers.parseDuration("1:02:34"))
        assertEquals(5L, Parsers.parseDuration("5"))
        assertEquals(0L, Parsers.parseDuration("abc"))
        assertEquals(0L, Parsers.parseDuration(null))
    }

    @Test
    fun parseViewCount_digitsOnly() {
        assertEquals(1_585_401L, Parsers.parseViewCount("1,585,401 views"))
        assertEquals(41L, Parsers.parseViewCount("41 videos"))
        assertEquals(0L, Parsers.parseViewCount("@Kotlin"))
        assertEquals(0L, Parsers.parseViewCount(null))
    }

    @Test
    fun parseCompactCount_suffixes() {
        assertEquals(99_800L, Parsers.parseCompactCount("99.8K subscribers"))
        assertEquals(4_520_000L, Parsers.parseCompactCount("4.52M"))
        assertEquals(1_200_000_000L, Parsers.parseCompactCount("1.2B views"))
        assertEquals(158_000L, Parsers.parseCompactCount("158K plays"))
        assertEquals(0L, Parsers.parseCompactCount("@Kotlin"))
    }

    @Test
    fun sourcesBest_picksLargest() {
        val o = JSONObject(
            """{"thumbnails":[
                {"url":"small.jpg","width":120,"height":90},
                {"url":"big.jpg","width":1280,"height":720}]}"""
        )
        assertEquals("big.jpg", Parsers.sourcesBest(o))
        assertNull(Parsers.sourcesBest(JSONObject("{}")))
    }

    @Test
    fun findFirst_depthFirst() {
        val root = JSONObject("""{"a":{"b":{"target":{"t":1}}}}""")
        assertNotNull(Parsers.findFirst(root, "target"))
        assertNull(Parsers.findFirst(root, "nope"))
    }

    // -------------------------------------------------------------- fixtures

    @Test
    fun searchAll_mixedLegacyAndLockupLayouts() {
        val page = fixture("search_all")

        val legacy = findAllByKey(page, "videoRenderer")
        assertEquals(25, legacy.size)
        val first = Parsers.parseVideoRenderer(legacy.first())!!
        assertEquals("xT8oP0wy-A0", first.id)
        assertEquals("Kotlin in 100 Seconds", first.title)
        assertEquals("Fireship", first.uploaderName)
        assertEquals(142L, first.duration)
        assertEquals(1_585_401L, first.viewCount)
        assertEquals("4 years ago", first.textualDate)
        assertTrue(first.url.contains("watch?v=xT8oP0wy-A0"))
        assertNotNull(first.thumbnailUrl)

        val channels = findAllByKey(page, "channelRenderer")
        assertEquals(1, channels.size)
        val channel = Parsers.parseChannelRenderer(channels.first())!!
        assertEquals("UCP7uiEZIqci43m22KDl0sNw", channel.id)
        assertEquals("Kotlin by JetBrains", channel.name)

        val lockups = findAllByKey(page, "lockupViewModel")
        val playlists = lockups.mapNotNull { Parsers.parseLockupPlaylist(it) }
        assertTrue(playlists.isNotEmpty())
        assertEquals("PLRKyZvuMYSIMW3-rSOGCkPlO1z_IYJy3G", playlists.first().id)
        assertEquals("Kotlin Beginner Tutorials Hindi | Complete Series", playlists.first().name)
        assertEquals("Cheezy Code", playlists.first().uploaderName)

        assertNotNull(Parsers.continuationToken(page))
    }

    @Test
    fun searchVideos_legacyLayoutAndContinuation() {
        val page = fixture("search_videos")
        val videos = findAllByKey(page, "videoRenderer").mapNotNull { Parsers.parseVideoRenderer(it) }
        assertEquals(19, videos.size)
        assertTrue(videos.all { it.uploaderName != null })
        assertNotNull(Parsers.continuationToken(page))

        val page2 = fixture("search_videos_page2")
        val more = findAllByKey(page2, "videoRenderer").mapNotNull { Parsers.parseVideoRenderer(it) }
        assertEquals(20, more.size)
        assertTrue(more.none { it.id in videos.map { v -> v.id }.toSet() } || more.isNotEmpty())
    }

    @Test
    fun searchChannels_allParsed() {
        val page = fixture("search_channels")
        val channels = findAllByKey(page, "channelRenderer").mapNotNull { Parsers.parseChannelRenderer(it) }
        assertEquals(20, channels.size)
        val first = channels.first()
        assertEquals("UCP7uiEZIqci43m22KDl0sNw", first.id)
        assertEquals("Kotlin by JetBrains", first.name)
        assertTrue(channels.all { it.name.isNotBlank() })
    }

    @Test
    fun searchPlaylists_lockupLayout() {
        val page = fixture("search_playlists")
        val playlists = findAllByKey(page, "lockupViewModel").mapNotNull { Parsers.parseLockupPlaylist(it) }
        assertTrue(playlists.size >= 10)
        val first = playlists.first()
        assertEquals("PLRKyZvuMYSIMW3-rSOGCkPlO1z_IYJy3G", first.id)
        assertTrue(first.streamCount > 0)
        assertTrue(playlists.all { it.name.isNotBlank() && it.url.contains("list=") })
    }

    @Test
    fun musicSearch_items() {
        val page = fixture("music_search")
        val items = findAllByKey(page, "musicResponsiveListItemRenderer")
            .mapNotNull { Parsers.parseMusicItem(it) }
        assertEquals(11, items.size)
        val first = items.first()
        assertEquals("L1H7W_-8adQ", first.id)
        assertEquals("Kotlin", first.title)
        assertEquals("Song", first.uploaderName)
        assertTrue(first.url.contains("watch?v=L1H7W_-8adQ"))
    }

    @Test
    fun channelBrowse_pageHeaderLayout() {
        val page = fixture("channel_browse")
        val channel: ChannelRef = Parsers.parseChannelHeader(page)!!
        assertEquals("UCP7uiEZIqci43m22KDl0sNw", channel.id)
        assertEquals("Kotlin by JetBrains", channel.name)
        assertEquals(99_800L, channel.subscriberCount)
        assertTrue(channel.description.orEmpty().contains("Concise"))
        assertNotNull(channel.avatarUrl)

        val tabs = Parsers.channelTabs(page)
        assertTrue(tabs.any { it.first == "Videos" })
        assertTrue(tabs.any { it.first == "Shorts" })

        val videos = findAllByKey(page, "lockupViewModel")
            .filter { it.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" }
            .mapNotNull { Parsers.parseLockupVideo(it) }
        assertTrue(videos.isNotEmpty())
        assertEquals("hXDw2cOxnpo", videos.first().id)
        assertEquals("Hot-Reloading Kotlin/Native | Gabriele Pappalardo", videos.first().title)
        assertEquals(1_100L, videos.first().viewCount)
        assertEquals("13 hours ago", videos.first().textualDate)
    }

    @Test
    fun playlistBrowse_classicPlaylistHeaderLayout() {
        val page = fixture("playlist_classic")
        val playlist: PlaylistRef = Parsers.parsePlaylistHeader(page)!!
        assertEquals("PLRKyZvuMYSIMW3-rSOGCkPlO1z_IYJy3G", playlist.id)
        assertEquals("Kotlin Beginner Tutorials Hindi | Complete Series", playlist.name)
        assertEquals("Cheezy Code", playlist.uploaderName)
        assertEquals(41L, playlist.streamCount)
        assertNotNull(playlist.thumbnailUrl)

        val videos = findAllByKey(page, "lockupViewModel")
            .filter { it.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" }
            .mapNotNull { Parsers.parseLockupVideo(it) }
        assertEquals(41, videos.size)
        assertEquals("NosAkIKgA4Y", videos.first().id)
        assertEquals(289_000L, videos.first().viewCount)
        assertEquals("6 years ago", videos.first().textualDate)
    }

    @Test
    fun playlistBrowse_newPageHeaderLayout() {
        val page = fixture("playlist_new")
        val playlist: PlaylistRef = Parsers.parsePlaylistHeader(page)!!
        assertEquals(
            "Android App Development Course in Kotlin - Zero to Android Champion",
            playlist.name,
        )
        assertEquals(90L, playlist.streamCount)
        assertTrue(playlist.url.contains("list=PLUhfM8afLE_NQbVaoIEhceR9npbY57Pdg"))
    }

    @Test
    fun playlistBrowse_fallbackIdWhenHeaderIncomplete() {
        assertNull(Parsers.parsePlaylistHeader(JSONObject("{}")))
        val bareHeader = JSONObject("""{"header":{"pageHeaderRenderer":{}}}""")
        val fallback: PlaylistRef = Parsers.parsePlaylistHeader(bareHeader, fallbackId = "fallback")!!
        assertEquals("fallback", fallback.name)
        assertEquals("fallback", fallback.id)
        val classicBare = JSONObject("""{"header":{"playlistHeaderRenderer":{}}}""")
        assertEquals("fallback", Parsers.parsePlaylistHeader(classicBare, fallbackId = "fallback")?.name)
    }

    @Test
    fun player_streamInfo() {
        val page = fixture("player")
        val info = Parsers.parseStreamInfo(page, "xT8oP0wy-A0")
        assertEquals("xT8oP0wy-A0", info.id)
        assertEquals("Kotlin in 100 Seconds", info.title)
        assertEquals("Fireship", info.uploaderName)
        assertEquals(141L, info.duration)
        assertTrue(info.viewCount > 0)
        assertTrue(info.videoStreams.isNotEmpty())
        assertTrue(info.videoOnlyStreams.isNotEmpty())
        assertTrue(info.audioStreams.isNotEmpty())
        assertTrue(info.videoStreams.all { it.url.startsWith("http") })
        assertTrue(info.videoStreams.any { it.hasVideo && it.hasAudio })
        assertTrue(info.videoOnlyStreams.all { it.hasVideo && !it.hasAudio })
        assertTrue(info.audioStreams.all { it.hasAudio && !it.hasVideo })
        assertTrue(info.audioStreams.none { it.mimeType.contains("video") })
        assertTrue(info.subtitles.isNotEmpty())
        assertNotNull(info.subtitles.first().languageTag)
        assertTrue(info.subtitles.all { it.url.contains("fmt=vtt") })
        assertNotNull(info.thumbnailUrl)
    }

    @Test
    fun homeFeed_anonymousEmpty() {
        val page = fixture("home_feed")
        assertTrue(findAllByKey(page, "videoRenderer").isEmpty())
        assertTrue(findAllByKey(page, "channelRenderer").isEmpty())
        assertTrue(findAllByKey(page, "lockupViewModel").isEmpty())
        assertNull(Parsers.continuationToken(page))
    }

    @Test
    fun watchPage_chapterMarkers() {
        val html = File("src/test/resources/fixtures/watch_chapters.html").readText()
        val playerResponse = Parsers.extractWatchPlayerResponse(html)
        assertNotNull(playerResponse)
        val chapters = Parsers.parseChapters(playerResponse!!)
        assertEquals(5, chapters.size)
        assertEquals("Introduction", chapters[0].title)
        assertEquals(0L, chapters[0].startSeconds)
        assertEquals("Chapter 1. Introduction to Linux Families", chapters[1].title)
        assertEquals(98L, chapters[1].startSeconds)
        assertEquals("Chapter 2. Linux Philosophy and Concepts", chapters[2].title)
        assertEquals(459L, chapters[2].startSeconds)
        assertEquals("Chapter 4. Graphical Interface", chapters[4].title)
        assertEquals(3936L, chapters[4].startSeconds)
        assertTrue(chapters.zipWithNext().all { it.first.startSeconds <= it.second.startSeconds })
    }

    @Test
    fun watchPage_noPlayerResponseReturnsNull() {
        assertNull(Parsers.extractWatchPlayerResponse("<html>no player data here</html>"))
        assertNull(Parsers.extractWatchPlayerResponse("var ytInitialPlayerResponse = ")) // truncated
        assertTrue(Parsers.parseChapters(JSONObject("{}")).isEmpty())
    }

    // ------------------------------------------------- playability + premieres

    private fun playerResponse(
        status: String,
        reason: String? = null,
        errorCode: String? = null,
    ): JSONObject {
        val statusObj = JSONObject().put("status", status)
        reason?.let { statusObj.put("reason", it) }
        errorCode?.let { statusObj.put("errorCode", it) }
        return JSONObject()
            .put(
                "playabilityStatus",
                statusObj,
            )
            .put(
                "videoDetails",
                JSONObject()
                    .put("videoId", "VID1")
                    .put("title", "T")
                    .put("lengthSeconds", "100")
                    .put(
                        "thumbnail",
                        JSONObject().put("thumbnails", org.json.JSONArray().put(JSONObject().put("url", "https://i.ytimg.com/x.jpg"))),
                    ),
            )
            .put("streamingData", JSONObject().put("formats", org.json.JSONArray()).put("adaptiveFormats", org.json.JSONArray()))
    }

    @Test(expected = PlayabilityException::class)
    fun streamInfo_privateThrowsPlayabilityException() {
        Parsers.parseStreamInfo(
            playerResponse("LOGIN_REQUIRED", errorCode = "Video unavailable"),
            "VID1",
        )
    }

    @Test
    fun playabilityKind_classifies() {
        assertEquals(
            PlayabilityKind.AGE_VERIFICATION,
            PlayabilityException.classify("LOGIN_REQUIRED", "Sign in to confirm your age", ""),
        )
        assertEquals(
            PlayabilityKind.AGE_RESTRICTED,
            PlayabilityException.classify("LOGIN_REQUIRED", "Age-restricted video", ""),
        )
        assertEquals(
            PlayabilityKind.PRIVATE,
            PlayabilityException.classify("UNPLAYABLE", "This video is private", "UNKNOWN"),
        )
        assertEquals(
            PlayabilityKind.REMOVED,
            PlayabilityException.classify("UNPLAYABLE", "This video has been removed by the uploader", ""),
        )
        assertEquals(
            PlayabilityKind.REGION_BLOCKED,
            PlayabilityException.classify("UNPLAYABLE", "Video not available in your country", ""),
        )
        assertEquals(
            PlayabilityKind.PREMIUM_REQUIRED,
            PlayabilityException.classify("UNPLAYABLE", "Members-only content", ""),
        )
        assertEquals(
            PlayabilityKind.PREMIUM_REQUIRED,
            PlayabilityException.classify("UNPLAYABLE", "Watch with YouTube Premium", ""),
        )
        assertEquals(
            PlayabilityKind.LIVE_NOT_STARTED,
            PlayabilityException.classify("LIVE_STREAM_OFFLINE", "Stream is offline", ""),
        )
        assertEquals(
            PlayabilityKind.UNPLAYABLE,
            PlayabilityException.classify("UNPLAYABLE", "something else", ""),
        )
    }

    @Test
    fun streamInfo_upcomingIsNotAnError() {
        val upcoming = playerResponse(
            "LIVE_STREAM_OFFLINE",
            reason = "This live event will begin soon",
        )
        upcoming.put(
            "videoDetails",
            JSONObject()
                .put("videoId", "VID1")
                .put("title", "T")
                .put("startTimestamp", "1754186400")
                .put("isUpcoming", true)
                .put("lengthSeconds", "100")
                .put("thumbnail", JSONObject().put("thumbnails", org.json.JSONArray().put(JSONObject().put("url", "https://i.ytimg.com/x.jpg")))),
        )
        val info = Parsers.parseStreamInfo(upcoming, "VID1")
        assertNotNull(info.premiereAt)
        assertEquals(1_754_186_400_000L, info.premiereAt)
    }

    @Test
    fun streamInfo_premieresWithoutUpcomingFlagHaveNoPremiereAt() {
        val info = Parsers.parseStreamInfo(playerResponse("OK"), "VID1")
        assertNull(info.premiereAt)
    }

    @Test
    fun streamInfo_finishedLiveStreamIsNotLive() {
        // YouTube keeps isLiveContent set on the VOD of a stream that has ended; only
        // isLive means "live right now".
        val ended = playerResponse("OK")
        ended.getJSONObject("videoDetails")
            .put("isLiveContent", true)
            .put("isLive", false)
        assertEquals(StreamType.NORMAL, Parsers.parseStreamInfo(ended, "VID1").streamType)
    }

    @Test
    fun streamInfo_liveNowIsLive() {
        val live = playerResponse("OK")
        live.getJSONObject("videoDetails")
            .put("isLiveContent", true)
            .put("isLive", true)
        assertEquals(StreamType.LIVE, Parsers.parseStreamInfo(live, "VID1").streamType)
    }
}
