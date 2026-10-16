package com.simplecityapps.playback.mediasession

import androidx.media3.common.MediaMetadata
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassicalMetadataTest {

    private val work = "Symphony No. 5 in C minor, Op. 67"

    private fun movement(id: Long, name: String) = testSong(id, name = name).copy(composer = "Beethoven", composerSort = "Beethoven, Ludwig van", conductor = "Kleiber", work = work)

    private fun metadata(song: Song) = MediaMetadata.Builder().setTitle(song.name).setArtist("Berliner Philharmoniker").build()

    @Test
    fun `a movement shows its name as the title and the composer and work as the artist`() {
        val queue = listOf(movement(1, "Allegro con brio"), movement(2, "Andante con moto"))

        val result = metadata(queue[1]).withClassicalText(queue, 1)

        assertEquals("II. Andante con moto", result.title)
        assertEquals("Beethoven: Symphony No. 5 · Kleiber", result.artist)
        assertEquals("Beethoven", result.composer)
    }

    @Test
    fun `a classical song outside a work block keeps its metadata`() {
        val lone = movement(1, "Allegro con brio")
        val original = metadata(lone)

        assertEquals(original, original.withClassicalText(listOf(lone), 0))
    }

    @Test
    fun `a non classical song keeps its metadata`() {
        val songs = listOf(testSong(1), testSong(2))
        val original = metadata(songs[0])

        val result = original.withClassicalText(songs, 0)

        assertEquals(original, result)
        assertNull(result.composer)
    }
}
