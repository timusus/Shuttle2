package com.simplecityapps.playback.queue

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Publishing a queue and moving through it, the rules `QueueStatePublisher` and [QueueModel] share. */
class QueuePublicationTest {
    private val songs = listOf(song(1), song(2), song(3))

    private fun QueueState.publish(
        uids: List<Long>,
        shuffled: List<Int> = uids.indices.toList(),
        current: Int? = 0,
        shuffleMode: ShuffleMode = ShuffleMode.Off
    ): QueueState? = republished(uids.size, shuffled, current, shuffleMode, isRestored = false) { index, isCurrent ->
        QueueItem(uids[index], songs[uids[index].toInt() - 1], isCurrent)
    }?.state

    @Test
    fun `an unchanged queue isn't republished`() {
        val state = checkNotNull(QueueState.Empty.publish(listOf(1, 2, 3)))

        state.publish(listOf(1, 2, 3)) shouldBe null
    }

    @Test
    fun `a move bumps the content version but not the non-move one - an edit bumps both`() {
        val state = checkNotNull(QueueState.Empty.publish(listOf(1, 2, 3)))
        val moved = checkNotNull(state.publish(listOf(2, 1, 3), current = 1))
        val edited = checkNotNull(moved.publish(listOf(2, 1)))

        moved.contentVersion shouldBe state.contentVersion + 1
        moved.nonMoveContentVersion shouldBe state.nonMoveContentVersion
        moved.currentPosition shouldBe 1
        edited.nonMoveContentVersion shouldBe moved.nonMoveContentVersion + 1
    }

    @Test
    fun `with shuffle on the presented order is the shuffled one`() {
        val state = checkNotNull(QueueState.Empty.publish(listOf(1, 2, 3), shuffled = listOf(2, 0, 1), current = 0, shuffleMode = ShuffleMode.On))

        state.items.map { it.uid } shouldBe listOf(3L, 1L, 2L)
        state.currentPosition shouldBe 1
    }

    @Test
    fun `next follows the repeat mode - previous doesn't wrap`() {
        val last = checkNotNull(QueueState.Empty.publish(listOf(1, 2, 3), current = 2))
        val first = checkNotNull(last.publish(listOf(1, 2, 3), current = 0))

        last.next(RepeatMode.Off) shouldBe null
        last.next(RepeatMode.All)?.uid shouldBe 1L
        last.next(RepeatMode.One)?.uid shouldBe 3L
        first.next(RepeatMode.Off)?.uid shouldBe 2L
        first.previous() shouldBe null
        last.previous()?.uid shouldBe 2L
        QueueState.Empty.next(RepeatMode.All) shouldBe null
    }
}
