package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IosWorkGainTest {
    private fun movement(id: Long) = song(id).copy(
        replayGainTrack = -6.0,
        replayGainAlbum = 3.0,
        work = "Symphony No. 5",
        composer = "Beethoven",
        genres = listOf("Classical")
    )

    private fun lone(id: Long) = song(id).copy(replayGainTrack = -6.0, replayGainAlbum = 3.0)

    private fun order(vararg songs: Song) = songs.mapIndexed { i, song -> QueueItem(uid = 100L + song.id, song = song, isCurrent = i == 0) }

    private fun ids(entries: List<IosQueueEntry>) = entries.map { it.songId }

    @Test
    fun `consecutive movements play at album gain and a lone song does not`() {
        val entries = workAlbumGainEntries(order(lone(1), movement(2), movement(3), lone(4)), classicalDisplay = true)
        ids(entries) shouldBe listOf(2L, 3L)
        entries.first() shouldBe IosQueueEntry(uid = 102, songId = 2)
    }

    @Test
    fun `neighbours are judged in the order given so a shuffle that splits a work drops it`() {
        ids(workAlbumGainEntries(order(movement(2), lone(1), movement(3)), classicalDisplay = true)) shouldBe emptyList()
        ids(workAlbumGainEntries(order(lone(1), movement(3), movement(2)), classicalDisplay = true)) shouldBe listOf(3L, 2L)
    }

    @Test
    fun `a movement without an album tag keeps track gain`() {
        val untagged = movement(3).copy(replayGainAlbum = null)
        ids(workAlbumGainEntries(order(movement(2), untagged), classicalDisplay = true)) shouldBe listOf(2L)
    }

    @Test
    fun `nothing is a work while classical display is off`() {
        workAlbumGainEntries(order(movement(2), movement(3)), classicalDisplay = false) shouldBe emptyList()
    }
}
