package app.librepipes.data.extractor

import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractorSearchOrderingTest {

    @Test
    fun channelsFirst_promotesChannelsWithinPageAndKeepsStableOrder() {
        val video1 = video("v1")
        val playlist1 = playlist("p1")
        val channel1 = channel("c1")
        val video2 = video("v2")
        val channel2 = channel("c2")
        val playlist2 = playlist("p2")

        val ordered = Extractor.channelsFirst(
            listOf(video1, playlist1, channel1, video2, channel2, playlist2),
        )

        assertEquals(
            listOf(channel1, channel2, video1, playlist1, video2, playlist2),
            ordered,
        )
    }

    @Test
    fun channelsFirst_appliedPerPageDoesNotMoveLaterChannelAboveEarlierPage() {
        val page1Video1 = video("page1-v1")
        val page1Channel = channel("page1-c1")
        val page1Video2 = video("page1-v2")
        val page2Video = video("page2-v1")
        val page2Channel = channel("page2-c1")
        val page2Playlist = playlist("page2-p1")

        val accumulated =
            Extractor.channelsFirst(listOf(page1Video1, page1Channel, page1Video2)) +
                Extractor.channelsFirst(listOf(page2Video, page2Channel, page2Playlist))

        assertEquals(
            listOf(
                page1Channel,
                page1Video1,
                page1Video2,
                page2Channel,
                page2Video,
                page2Playlist,
            ),
            accumulated,
        )
    }

    private fun video(id: String) = Extractor.SearchItem.Video(
        StreamRef(id = id, title = id, url = "https://youtube.com/watch?v=$id"),
    )

    private fun channel(id: String) = Extractor.SearchItem.Channel(
        ChannelRef(id = id, name = id, url = "https://youtube.com/channel/$id"),
    )

    private fun playlist(id: String) = Extractor.SearchItem.Playlist(
        PlaylistRef(id = id, name = id, url = "https://youtube.com/playlist?list=$id"),
    )
}
