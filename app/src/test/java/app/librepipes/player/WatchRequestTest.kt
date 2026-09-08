package app.librepipes.player

import app.librepipes.data.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchRequestTest {

    @Test
    fun takeReturnsCompleteQueueAndConsumesRequest() {
        val first = ref("first")
        val queue = listOf(first, ref("second"), ref("third"))

        WatchRequest.set(first, queue)

        assertEquals(first to queue, WatchRequest.take(first.url))
        assertNull(WatchRequest.take(first.url))
    }

    @Test
    fun mismatchedUrlDoesNotConsumeRequest() {
        val first = ref("kept")
        val queue = listOf(first, ref("next"))

        WatchRequest.set(first, queue)

        assertNull(WatchRequest.take("https://youtube.com/watch?v=other"))
        assertEquals(first to queue, WatchRequest.take(first.url))
    }

    private fun ref(id: String) = StreamRef(
        id = id,
        title = id,
        url = "https://youtube.com/watch?v=$id",
    )
}
