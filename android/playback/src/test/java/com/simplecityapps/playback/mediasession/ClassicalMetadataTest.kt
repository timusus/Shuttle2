package com.simplecityapps.playback.mediasession

import androidx.media3.common.MediaMetadata
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassicalMetadataTest {

    private val work = "Symphony No. 5 in C minor, Op. 67"

    private fun movement(id: Long, name: String) = testSong(id, name = name).copy(composer = "Beethoven", composerSort = "Beethoven, Ludwig van", conductor = "Kleiber", work = work)

    private fun items(vararg songs: Song) = songs.mapIndexed { index, song -> QueueItem(index + 1L, song, isCurrent = index == 0) }

    private fun MediaMetadata.withText(queue: List<QueueItem>, index: Int) = classicalText(queue, index)?.let { withClassicalText(it) } ?: this

    private fun metadata(song: Song) = MediaMetadata.Builder().setTitle(song.name).setArtist("Berliner Philharmoniker").build()

    @Test
    fun `a movement shows its name as the title and the composer and work as the artist`() {
        val queue = items(movement(1, "Allegro con brio"), movement(2, "Andante con moto"))

        val result = metadata(queue[1].song).withText(queue, 1)

        assertEquals("II. Andante con moto", result.title)
        assertEquals("Beethoven: Symphony No. 5 · Kleiber", result.artist)
        assertEquals("Beethoven", result.composer)
    }

    @Test
    fun `a classical song outside a work block keeps its metadata`() {
        val lone = movement(1, "Allegro con brio")
        val original = metadata(lone)

        assertEquals(original, original.withText(items(lone), 0))
    }

    @Test
    fun `pop songs sharing a work tag keep their metadata`() {
        val pops = items(
            testSong(1, name = "Hold On").copy(work = "Hold On", composer = "Wilson Phillips", genres = listOf("Pop")),
            testSong(2, name = "Hold On").copy(work = "Hold On", composer = "Wilson Phillips", genres = listOf("Pop"))
        )
        val original = metadata(pops[1].song)

        assertEquals(original, original.withText(pops, 1))
    }

    @Test
    fun `a non classical song keeps its metadata`() {
        val songs = items(testSong(1), testSong(2))
        val original = metadata(songs[0].song)

        val result = original.withText(songs, 0)

        assertEquals(original, result)
        assertNull(result.composer)
    }
}
