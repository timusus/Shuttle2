package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SystemSurfaceTextTest {
    // No work tag: the work is inferred from the shared "Symphony No. 5" prefix, so the neighbours are needed.
    private val symphony = listOf("II. Andante con moto", "III. Allegro").mapIndexed { i, n ->
        song(id = i + 1L, name = "Symphony No. 5 in C minor, Op. 67: $n", albumArtist = "Orchestra", artists = listOf("Orchestra"), album = "Disc")
            .copy(composer = "Beethoven")
    }

    @Test
    fun `a movement of an inferred work shows the movement and the composer and work`() {
        val text = systemSurfaceText(symphony, 1, classicalDisplay = true)!!

        text.title shouldBe "III. Allegro"
        text.artist!!.startsWith("Beethoven: Symphony No. 5") shouldBe true
        text.album shouldBe "Disc"
    }

    @Test
    fun `the setting off leaves the song plain`() {
        systemSurfaceText(symphony, 1, classicalDisplay = false) shouldBe
            SystemSurfaceText(symphony[1].name.orEmpty(), symphony[1].friendlyArtistName, "Disc")
    }

    @Test
    fun `a pop song stays plain`() {
        val pop = listOf(song(id = 1, name = "Hold On - Live", albumArtist = "Band", artists = listOf("Band"), album = "LP"))

        systemSurfaceText(pop, 0, classicalDisplay = true) shouldBe SystemSurfaceText("Hold On - Live", "Band", "LP")
    }

    @Test
    fun `an index outside the queue has no text`() {
        systemSurfaceText(symphony, 5, classicalDisplay = true) shouldBe null
    }
}
