package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NowPlayingWorkTest {
    private fun movement(id: Long, work: String?, name: String, duration: Int = 60_000) = song(id = id, name = name, albumArtist = "Berliner Philharmoniker", track = id.toInt(), duration = duration, genres = listOf("Classical"))
        .copy(composer = "Beethoven", composerSort = "Beethoven, Ludwig van", conductor = "Kleiber", work = work)

    private fun symphony() = listOf("Allegro con brio", "Andante con moto", "Scherzo", "Allegro").mapIndexed { i, name -> movement(i + 1L, "Symphony No. 5 in C minor, Op. 67", name) }

    @Test
    fun `the middle of a symphony is described by composer work movement and performers`() {
        val work = nowPlayingWork(symphony(), 1)!!
        work.composer shouldBe "Beethoven"
        work.work shouldBe "Symphony No. 5"
        work.detail shouldBe "C minor · Op. 67"
        work.movement shouldBe "II. Andante con moto"
        work.performers shouldBe "Kleiber"
        work.miniTitle shouldBe "Beethoven: Symphony No. 5"
        work.miniSubtitle shouldBe "II. Andante con moto · Kleiber"
        work.ordinal shouldBe "II"
        work.total shouldBe "IV"
    }

    @Test
    fun `every track of the work is listed with its numeral name and length`() {
        val tracks = nowPlayingWork(symphony(), 1)!!.tracks
        tracks.map { it.numeral } shouldBe listOf("I", "II", "III", "IV")
        tracks.map { it.name } shouldBe listOf("Allegro con brio", "Andante con moto", "Scherzo", "Allegro")
        tracks.map { it.durationMs } shouldBe List(4) { 60_000L }
    }

    @Test
    fun `a song outside a work block has no work model`() {
        nowPlayingWork(listOf(movement(1, "Moonlight Sonata", "Adagio")), 0) shouldBe null
        nowPlayingWork(listOf(song(id = 1, name = "Hello"), song(id = 2, name = "World")), 0) shouldBe null
    }

    @Test
    fun `the fill of the current segment follows the position`() {
        val ribbon = nowPlayingWork(symphony(), 2)!!.ribbon
        ribbon.segmentFill(0) shouldBe 0f
        ribbon.segmentFill(30_000) shouldBe 0.5f
        ribbon.segmentFill(90_000) shouldBe 1f
    }

    @Test
    fun `an act of several movements fills across its movements`() {
        val songs = listOf("Act 1: Overture", "Act 1: Aria", "Act 2: Duet").mapIndexed { i, name -> movement(i + 1L, "La Traviata", name) }
        val ribbon = nowPlayingWork(songs, 1)!!.ribbon
        ribbon.segments shouldBe listOf(120_000L, 60_000L)
        ribbon.currentSegment shouldBe 0
        ribbon.segmentFill(30_000) shouldBe 0.75f
    }

    @Test
    fun `remaining time reads minutes and seconds then hours`() {
        formatWorkRemaining(24 * 60_000L + 10_000) shouldBe "24:10"
        formatWorkRemaining(5_000) shouldBe "0:05"
        formatWorkRemaining(3_725_000) shouldBe "1:02:05"
    }

    @Test
    fun `a movement split over tracks counts once in the caption`() {
        val names = listOf("I. Allegro", "II. Andante", "III. Scherzo", "IV. Urlicht", "V. Finale", "V. Wieder sehr breit", "V. Langsam", "V. Mit Aufschwung")
        val songs = names.mapIndexed { i, name -> movement(i + 1L, "Symphony No. 2", name) }

        val third = nowPlayingWork(songs, 2)!!
        third.ordinal shouldBe "III"
        third.total shouldBe "V"

        val continuation = nowPlayingWork(songs, 6)!!
        continuation.ordinal shouldBe "V"
        continuation.movement shouldBe "V. Langsam"
        continuation.ribbon.movementCount shouldBe 5
        continuation.ribbon.segments.size shouldBe 5
        continuation.ordinals shouldBe listOf("I", "II", "III", "IV", "V", "V", "V", "V")
    }

    @Test
    fun `the tagged movement number and total win over the track count`() {
        val songs = listOf("Kyrie", "Gloria", "Gloria part two", "Credo").mapIndexed { i, name ->
            movement(i + 1L, "Mass in B minor", name).copy(
                movementNumber = if (i < 2) {
                    i + 1
                } else if (i == 2) {
                    2
                } else {
                    3
                },
                movementTotal = 3
            )
        }
        val work = nowPlayingWork(songs, 2)!!
        work.ordinal shouldBe "II"
        work.total shouldBe "III"
    }

    @Test
    fun `a partial work never reads past its total`() {
        val songs = listOf("II. Andante", "III. Scherzo").mapIndexed { i, name -> movement(i + 1L, "Symphony No. 5", name) }
        val work = nowPlayingWork(songs, 1)!!
        work.ordinal shouldBe "III"
        work.total shouldBe "III"
    }

    @Test
    fun `a gapped work totals at its highest tagged number`() {
        val songs = listOf(1, 2, 4).mapIndexed { i, number -> movement(i + 1L, "Suite", "Dance $number").copy(movementNumber = number) }
        val work = nowPlayingWork(songs, 2)!!
        work.ordinal shouldBe "IV"
        work.total shouldBe "IV"
    }

    @Test
    fun `two works that both number their movements from one do not continue each other`() {
        val first = listOf("Allegro", "Adagio").mapIndexed { i, name -> movement(i + 1L, "Symphony No. 1", name).copy(movementNumber = i + 1) }
        val second = listOf("Allegro", "Adagio").mapIndexed { i, name -> movement(i + 3L, "Symphony No. 2", name).copy(movementNumber = i + 1) }
        val queue = first + second
        val work = nowPlayingWork(queue, 2)!!
        work.work shouldBe "Symphony No. 2"
        work.movement shouldBe "I. Allegro"
        work.ordinal shouldBe "I"
        work.total shouldBe "II"
    }

    @Test
    fun `without movement numbers the caption counts tracks`() {
        val songs = listOf("Prologue", "Dance", "Finale").mapIndexed { i, name -> movement(i + 1L, "Suite", name) }
        val work = nowPlayingWork(songs, 1)!!
        work.ordinal shouldBe "II"
        work.total shouldBe "III"
    }

    @Test
    fun `thirty movements make a continuous ribbon`() {
        val songs = List(30) { movement(it + 1L, "Goldberg Variations, BWV 988", "Variatio ${it + 1}") }
        val work = nowPlayingWork(songs, 4)!!
        work.ribbon.continuous shouldBe true
        work.ordinal shouldBe "5"
        work.total shouldBe "30"
    }
}
