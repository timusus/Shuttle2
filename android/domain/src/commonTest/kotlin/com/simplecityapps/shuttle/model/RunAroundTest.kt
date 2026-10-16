package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RunAroundTest {
    private fun movement(id: Long, work: String?, name: String, composer: String = "Beethoven") = song(id = id, name = name, albumArtist = "Berliner Philharmoniker", track = id.toInt(), genres = listOf("Classical"))
        .copy(composer = composer, conductor = "Kleiber", work = work)

    private fun pop(id: Long, work: String? = null, name: String = "Hold On") = song(id = id, name = name, albumArtist = "Wilson Phillips", genres = listOf("Pop"))
        .copy(composer = "Wilson Phillips", work = work)

    private val queue = listOf(movement(1, null, "Intro")) +
        List(4) { movement(2L + it, "Symphony No. 5", "Movement ${it + 1}") } +
        List(3) { movement(6L + it, "Symphony No. 6", "Movement ${it + 1}", composer = "Beethoven") } +
        movement(9, null, "Outro")

    @Test
    fun `the local run equals the whole-queue run at the start and middle and end of a work`() {
        val whole = findRuns(queue)
        queue.indices.forEach { index ->
            val expected = whole.first { index in it.range }
            val actual = runAround(queue, index)
            actual.range shouldBe expected.range
            actual.work shouldBe expected.work
            actual.inferred shouldBe expected.inferred
        }
        runAround(queue, 1).range shouldBe 1..4
        runAround(queue, 3).range shouldBe 1..4
        runAround(queue, 4).range shouldBe 1..4
    }

    @Test
    fun `a long queue of unrelated songs is scanned no further than the neighbours`() {
        var reads = 0
        val songs = List(10_000) { pop(it + 1L, name = "Song $it").copy(work = null) }
        val counting = object : AbstractList<Song>() {
            override val size get() = songs.size
            override fun get(index: Int): Song = songs[index].also { reads++ }
        }
        runAround(counting, 5_000).range shouldBe 5_000..5_000
        reads shouldBeLessThanOrEqual 1
        workProgress(counting, 5_000) shouldBe null
        lockScreenText(counting, 5_000) shouldBe null
        reads shouldBeLessThanOrEqual 10
    }

    @Test
    fun `pop songs sharing a work tag get no movement text`() {
        val songs = listOf(pop(1, "Hold On"), pop(2, "Hold On"))
        workProgress(songs, 0) shouldBe null
        nowPlayingWork(songs, 1) shouldBe null
        runAround(songs, 1).range shouldBe 1..1
    }

    @Test
    fun `a classical work is still found in a queue of pop`() {
        val songs = listOf(pop(1), pop(2, name = "Other")) + List(3) { movement(10L + it, "Symphony No. 5", "Movement ${it + 1}") } + pop(3)
        runAround(songs, 3).range shouldBe 2..4
        workProgress(songs, 3)!!.movementCount shouldBe 3
    }
}
