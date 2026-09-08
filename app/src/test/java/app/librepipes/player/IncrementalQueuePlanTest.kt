package app.librepipes.player

import app.librepipes.data.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalQueuePlanTest {

    @Test
    fun firstSelectionAppendsEverythingAfterIt() {
        val plan = incrementalQueuePlan("one", refs("one", "two", "three"))

        assertEquals(emptyList<StreamRef>(), plan.before)
        assertEquals(listOf("two", "three"), plan.after.map { it.id })
    }

    @Test
    fun middleSelectionSplitsQueueAroundSelectedItem() {
        val plan = incrementalQueuePlan("two", refs("one", "two", "three"))

        assertEquals(listOf("one"), plan.before.map { it.id })
        assertEquals(listOf("three"), plan.after.map { it.id })
    }

    @Test
    fun lastSelectionInsertsEverythingBeforeIt() {
        val plan = incrementalQueuePlan("three", refs("one", "two", "three"))

        assertEquals(listOf("one", "two"), plan.before.map { it.id })
        assertEquals(emptyList<StreamRef>(), plan.after)
    }

    @Test
    fun duplicatesAreRemovedWithoutChangingPlaylistOrder() {
        val plan = incrementalQueuePlan("two", refs("one", "two", "one", "three", "two"))

        assertEquals(listOf("one"), plan.before.map { it.id })
        assertEquals(listOf("three"), plan.after.map { it.id })
    }

    @Test
    fun skippedUnavailableItemsKeepPlayableOrderAndSelectedIndex() {
        val plan = incrementalQueuePlan("three", refs("one", "unavailable", "two", "three", "four"))
        val playableIds = setOf("one", "two", "three", "four")
        val finalQueue = plan.before.filter { it.id in playableIds }.map { it.id } +
            "three" + plan.after.filter { it.id in playableIds }.map { it.id }

        assertEquals(listOf("one", "two", "three", "four"), finalQueue)
        assertEquals(2, finalQueue.indexOf("three"))
    }

    @Test
    fun newGenerationMakesPreviousPopulationStale() {
        val generations = QueueReplacementGeneration()
        val first = generations.next()

        assertTrue(generations.isCurrent(first))
        val second = generations.next()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    private fun refs(vararg ids: String) = ids.map { id ->
        StreamRef(
            id = id,
            title = id,
            url = "https://youtube.com/watch?v=$id",
        )
    }
}
