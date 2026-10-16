package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShuffleWorksTest {

    private val fakeQueueOperations = FakeQueueOperations()
    private val shuffleWorks = ShuffleWorks(PlaySongs(fakeQueueOperations, FakePlaybackOperations()))

    private fun movement(id: Long, work: String) = createSong(id = id, name = "Movement $id").copy(work = work, composer = "Ludwig Harrow")

    @Test
    fun `never splits or reorders a work's movements, whatever the shuffle`() = runTest {
        val songs = listOf(
            movement(1, "Sonata No. 3"),
            movement(2, "Sonata No. 3"),
            movement(3, "Sonata No. 3"),
            createSong(id = 4, name = "Encore"),
            movement(5, "Prelude in D"),
            movement(6, "Prelude in D"),
        )

        repeat(30) { seed ->
            shuffleWorks(songs, random = Random(seed)).shouldBeInstanceOf<ShuffleWorks.Result.Success>()

            val ids = fakeQueueOperations.lastSetQueue!!.map { it.id }
            ids.sorted() shouldBe listOf(1L, 2L, 3L, 4L, 5L, 6L)
            ids.windowed(3).count { it == listOf(1L, 2L, 3L) } shouldBe 1
            ids.windowed(2).count { it == listOf(5L, 6L) } shouldBe 1
        }
    }

    @Test
    fun `shuffles the works themselves`() = runTest {
        val songs = (1L..8L).map { id -> movement(id, "Work ${(id + 1) / 2}") }

        val orders = (0 until 20).map { seed ->
            shuffleWorks(songs, random = Random(seed))
            fakeQueueOperations.lastSetQueue!!.map { it.id }
        }

        (orders.toSet().size > 1) shouldBe true
    }

    @Test
    fun `reports a queue that could not be set`() = runTest {
        fakeQueueOperations.setQueueResult = false

        shuffleWorks(listOf(movement(1, "Sonata"), movement(2, "Sonata"))).shouldBeInstanceOf<ShuffleWorks.Result.Failure>()
    }
}
