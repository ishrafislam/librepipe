package app.librepipes.data.extractor

import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.data.youtube.InnertubeClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFeedTest {

    @Test
    fun initialPageWithoutContinuationIsComplete() {
        val feed = feed(nextToken = null, videos = listOf(video("one")))

        assertFalse(feed.hasMore)
    }

    @Test
    fun initialPageWithContinuationHasMoreAndDeduplicatesVideos() {
        val first = video("one")
        val second = video("two")
        val feed = feed(nextToken = "next", videos = listOf(first, second, first))

        assertTrue(feed.hasMore)
        assertEquals(listOf(first, second), feed.videos)
    }

    private fun feed(nextToken: String?, videos: List<StreamRef>) = Extractor.PlaylistFeed(
        client = InnertubeClient(OkHttpClient()),
        nextToken = nextToken,
        playlist = PlaylistRef(
            id = "playlist",
            name = "Playlist",
            url = "https://youtube.com/playlist?list=playlist",
        ),
        initialVideos = videos,
    )

    private fun video(id: String) = StreamRef(
        id = id,
        title = id,
        url = "https://youtube.com/watch?v=$id",
    )
}
