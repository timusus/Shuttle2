package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** That the controller hands the player the work set ahead of every order it sends. */
class IosPlayerControllerWorkGainTest : IosPlayerControllerTestBase() {
    private fun movement(id: Long) = song(id).copy(replayGainAlbum = 3.0, work = "Symphony No. 5", composer = "Beethoven", genres = listOf("Classical"))

    private val queue: List<Song> = listOf(a, movement(2), movement(3), d)

    @Test
    fun `starting a queue with a work sends the work set before the items`() = test { controller ->
        classicalDisplay = true
        controller.start(queue, play = false)

        engine.orderings shouldBe listOf("work [2, 3]", "items")
    }

    @Test
    fun `toggling shuffle sends the work set of the new order before replacing the items`() = test { controller ->
        classicalDisplay = true
        controller.start(queue)
        engine.orderings.clear()

        controller.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = true)
        engine.settle()

        val shuffled = controller.queueOperations.queueStateFlow.value.items
        engine.orderings.last() shouldBe "replace"
        engine.orderings.dropLast(1).lastOrNull() shouldBe "work ${workAlbumGainEntries(shuffled, classicalDisplay = true).map { it.songId }}"
        engine.workAlbumGains.last() shouldBe workAlbumGainEntries(shuffled, classicalDisplay = true)
    }

    @Test
    fun `removing a movement sends the smaller work set before replacing the items`() = test { controller ->
        classicalDisplay = true
        controller.start(queue)
        engine.orderings.clear()

        controller.queueOperations.remove(controller.queueOperations.queueStateFlow.value.items.filter { it.song.id == 3L })
        engine.settle()

        engine.orderings shouldBe listOf("work []", "replace")
    }

    @Test
    fun `refreshing the work gain follows classical display`() = test { controller ->
        classicalDisplay = true
        controller.start(queue)

        classicalDisplay = false
        controller.refreshWorkGain()
        engine.workAlbumGains.last() shouldBe emptyList()

        classicalDisplay = true
        controller.refreshWorkGain()
        engine.workAlbumGains.last().map { it.songId } shouldBe listOf(2L, 3L)
    }
}
