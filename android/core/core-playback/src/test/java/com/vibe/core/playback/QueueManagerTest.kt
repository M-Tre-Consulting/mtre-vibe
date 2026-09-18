package com.vibe.core.playback

import com.vibe.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueManagerTest {

    private lateinit var queueManager: QueueManager

    private fun dummyTrack(id: String, name: String = "Track $id"): Track {
        return Track(
            id = id,
            name = name,
            uri = "spotify:track:$id",
            durationMs = 180000L,
            artists = emptyList(),
            album = com.vibe.core.model.AlbumSummary(id = "a1", name = "Album", uri = "spotify:album:a1")
        )
    }

    @Before
    fun setup() {
        queueManager = QueueManager()
    }

    @Test
    fun `setContextQueue populates context queue starting after currently playing track`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"), dummyTrack("4"))
        queueManager.setContextQueue(currentTrack = tracks[1], contextTracks = tracks, contextName = "Album A")

        val state = queueManager.queue.value
        assertEquals("2", state.currentlyPlaying?.id)
        assertEquals("Album A", state.contextName)
        assertEquals(2, state.contextQueue.size)
        assertEquals("3", state.contextQueue[0].id)
        assertEquals("4", state.contextQueue[1].id)
        assertTrue(state.userQueue.isEmpty())
    }

    @Test
    fun `reordering context queue changes advanceToNext order`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"), dummyTrack("4"))
        queueManager.setContextQueue(currentTrack = tracks[0], contextTracks = tracks)

        // Initial contextQueue: 2, 3, 4
        // Move track 4 (index 2) to top (index 0)
        // First move 2 -> 1, then 1 -> 0 as drag gestures do
        queueManager.moveContextQueueItem(2, 1)
        queueManager.moveContextQueueItem(1, 0)

        val state = queueManager.queue.value
        assertEquals("4", state.contextQueue[0].id)
        assertEquals("2", state.contextQueue[1].id)
        assertEquals("3", state.contextQueue[2].id)

        // Advance to next: should play track 4!
        val next = queueManager.advanceToNext()
        assertEquals("4", next?.id)
        assertEquals("4", queueManager.queue.value.currentlyPlaying?.id)
        assertEquals(2, queueManager.queue.value.contextQueue.size)
        assertEquals("2", queueManager.queue.value.contextQueue[0].id)
    }

    @Test
    fun `userQueue items play before contextQueue items and respect reordering`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"))
        queueManager.setContextQueue(currentTrack = tracks[0], contextTracks = tracks)

        queueManager.addToUserQueue(dummyTrack("user_A"))
        queueManager.addToUserQueue(dummyTrack("user_B"))

        // Reorder user queue so user_B is first
        queueManager.moveUserQueueItem(1, 0)

        // Advance 1: should be user_B
        val first = queueManager.advanceToNext()
        assertEquals("user_B", first?.id)

        // Advance 2: should be user_A
        val second = queueManager.advanceToNext()
        assertEquals("user_A", second?.id)

        // Advance 3: should fall back to contextQueue (track 2)
        val third = queueManager.advanceToNext()
        assertEquals("2", third?.id)
    }

    @Test
    fun `removeTrack removes track from userQueue and contextQueue`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"))
        queueManager.setContextQueue(currentTrack = tracks[0], contextTracks = tracks)
        queueManager.addToUserQueue(dummyTrack("user_X"))

        queueManager.removeTrack(dummyTrack("2"))
        queueManager.removeTrack(dummyTrack("user_X"))

        val state = queueManager.queue.value
        assertFalse(state.contextQueue.any { it.id == "2" })
        assertFalse(state.userQueue.any { it.id == "user_X" })
        assertEquals(1, state.contextQueue.size)
        assertEquals("3", state.contextQueue[0].id)
    }

    @Test
    fun `advanceToPrevious restores track from history to current playing`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"))
        queueManager.setContextQueue(currentTrack = tracks[0], contextTracks = tracks)

        queueManager.advanceToNext() // now playing track 2, track 1 in history
        assertEquals("2", queueManager.queue.value.currentlyPlaying?.id)
        assertEquals(1, queueManager.queue.value.recentHistory.size)

        val prev = queueManager.advanceToPrevious()
        assertEquals("1", prev?.id)
        assertEquals("1", queueManager.queue.value.currentlyPlaying?.id)
    }

    @Test
    fun `clearQueue removes all pending items`() {
        val tracks = listOf(dummyTrack("1"), dummyTrack("2"), dummyTrack("3"))
        queueManager.setContextQueue(currentTrack = tracks[0], contextTracks = tracks)
        queueManager.addToUserQueue(dummyTrack("X"))

        queueManager.clearQueue()
        val state = queueManager.queue.value
        assertTrue(state.userQueue.isEmpty())
        assertTrue(state.contextQueue.isEmpty())
        assertEquals("1", state.currentlyPlaying?.id)
    }
}
