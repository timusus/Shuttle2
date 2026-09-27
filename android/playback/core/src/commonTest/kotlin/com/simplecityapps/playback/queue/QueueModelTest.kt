package com.simplecityapps.playback.queue

import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

/** The queue's rules, as `PlaylistEditor` and `QueueStatePublisher` apply them on Android; the setQueue cases mirror `NewQueueTest`. */
class QueueModelTest {
    private val queue = QueueModel(Random(1))

    private val a = song(1)
    private val b = song(2)
    private val c = song(3)
    private val d = song(4)

    private fun presented() = queue.queueStateFlow.value.items.map { it.song }

    @Test
    fun `with shuffle on - the position is in the saved shuffled order - a song held twice included`() {
        queue.setShuffleMode(ShuffleMode.On, reshuffle = false)
        queue.setRepeatMode(RepeatMode.All)

        queue.setQueue(listOf(a, b, a, c), shuffleSongs = listOf(a, c, b, a), position = 3, shuffleMode = ShuffleMode.On)

        val state = queue.queueStateFlow.value
        presented() shouldBe listOf(a, c, b, a)
        state.items.map { it.uid }.toSet().size shouldBe 4
        state.currentPosition shouldBe 3
        // The second copy of a, not the first.
        queue.lists.base.indexOf(state.currentItem) shouldBe 2
        queue.lists.base.map { it.song } shouldBe listOf(a, b, a, c)
        queue.shuffleMode shouldBe ShuffleMode.On
        queue.repeatMode shouldBe RepeatMode.All
    }

    @Test
    fun `with shuffle on - a saved current song no longer queued starts at the start of the shuffled order`() {
        queue.setShuffleMode(ShuffleMode.On, reshuffle = false)

        queue.setQueue(listOf(a, b, c), shuffleSongs = listOf(c, song(9), a, b), position = 1, shuffleMode = ShuffleMode.On)

        presented() shouldBe listOf(c, a, b)
        queue.currentItem?.song shouldBe c
    }

    @Test
    fun `the shuffle mode is set with the queue - so the position is in its order whatever the mode was`() {
        val songs = listOf(a, b, c, d)
        val shuffleSongs = listOf(c, a, d, b)

        queue.setQueue(songs, shuffleSongs, position = 0, shuffleMode = ShuffleMode.On)

        queue.shuffleMode shouldBe ShuffleMode.On
        presented() shouldBe shuffleSongs
        queue.currentItem?.song shouldBe c
    }

    @Test
    fun `a queue set with shuffle off is in the saved order - though shuffle was on`() {
        val songs = listOf(a, b, c, d)
        queue.setShuffleMode(ShuffleMode.On, reshuffle = false)

        queue.setQueue(songs, shuffleSongs = songs.reversed(), position = 1, shuffleMode = ShuffleMode.Off)

        queue.shuffleMode shouldBe ShuffleMode.Off
        presented() shouldBe songs
        queue.currentItem?.song shouldBe b
    }

    @Test
    fun `a new queue turns shuffle off unless it's retained`() {
        queue.setQueue(listOf(a, b), null, 0, shuffleMode = ShuffleMode.On)

        queue.setQueue(listOf(c, d), null, 1, retainShuffle = true)
        queue.shuffleMode shouldBe ShuffleMode.On
        queue.currentItem?.song shouldBe d
        presented().first() shouldBe d

        queue.setQueue(listOf(a, b), null, 0)
        queue.shuffleMode shouldBe ShuffleMode.Off
    }

    @Test
    fun `setting the same songs keeps their items`() {
        queue.setQueue(listOf(a, b, c), null, 0)
        val uids = queue.lists.base.map { it.uid }

        queue.setQueue(listOf(a, b, c), null, 2)

        queue.lists.base.map { it.uid } shouldBe uids
        queue.currentItem?.song shouldBe c
    }

    @Test
    fun `an empty queue or a position out of range is refused`() {
        queue.setQueue(emptyList(), null, 0) shouldBe false
        queue.setQueue(listOf(a), null, 1) shouldBe false
        queue.queueStateFlow.value.items shouldBe emptyList()
    }

    @Test
    fun `next follows the repeat mode - previous doesn't wrap`() {
        queue.setQueue(listOf(a, b, c), null, 2)

        queue.next()?.song shouldBe null
        queue.next(RepeatMode.All)?.song shouldBe a
        queue.next(RepeatMode.One)?.song shouldBe c
        queue.previous()?.song shouldBe b

        queue.setCurrent(queue.lists.base[0].uid)
        queue.previous() shouldBe null
    }

    @Test
    fun `toggling shuffle keeps the current item - first in the new order`() {
        queue.setQueue(listOf(a, b, c, d), null, 2)
        val current = queue.currentItem

        queue.setShuffleMode(ShuffleMode.On, reshuffle = true)
        queue.currentItem shouldBe current
        queue.queueStateFlow.value.currentPosition shouldBe 0

        queue.setShuffleMode(ShuffleMode.Off, reshuffle = false)
        queue.currentItem shouldBe current
        queue.queueStateFlow.value.currentPosition shouldBe 2
    }

    @Test
    fun `songs played next go after the current item in both orders`() {
        queue.setQueue(listOf(a, b, c), null, 0)
        queue.setShuffleMode(ShuffleMode.On, reshuffle = true)

        queue.addNext(listOf(d)) shouldBe false

        queue.next()?.song shouldBe d
        queue.lists.base.map { it.song } shouldBe listOf(a, d, b, c)
        queue.lists.shuffled[1].song shouldBe d
    }

    @Test
    fun `songs added to an empty queue make a new one`() {
        queue.add(listOf(a, b)) shouldBe true
        queue.currentItem?.song shouldBe a
        queue.add(listOf(c)) shouldBe false
        queue.lists.base.map { it.song } shouldBe listOf(a, b, c)
    }

    @Test
    fun `a move with shuffle off reorders the queue and keeps the current item`() {
        queue.setQueue(listOf(a, b, c, d), null, 1)
        val versions = queue.queueStateFlow.value

        queue.move(1, 3)

        presented() shouldBe listOf(a, c, d, b)
        queue.currentItem?.song shouldBe b
        queue.queueStateFlow.value.currentPosition shouldBe 3
        queue.queueStateFlow.value.nonMoveContentVersion shouldBe versions.nonMoveContentVersion
        queue.queueStateFlow.value.contentVersion shouldBe versions.contentVersion + 1
    }

    @Test
    fun `a move with shuffle on reorders only the shuffled order`() {
        queue.setQueue(listOf(a, b, c, d), listOf(d, c, b, a), 0, shuffleMode = ShuffleMode.On)

        queue.move(0, 2)

        presented() shouldBe listOf(c, b, d, a)
        queue.lists.base.map { it.song } shouldBe listOf(a, b, c, d)
        queue.currentItem?.song shouldBe d
    }

    @Test
    fun `removing the current item moves to the next that stays`() {
        queue.setQueue(listOf(a, b, c, d), null, 1)
        val (_, itemB, itemC) = queue.lists.base

        queue.remove(setOf(itemB.uid, itemC.uid)) shouldBe false

        queue.currentItem?.song shouldBe d
        presented() shouldBe listOf(a, d)
    }

    @Test
    fun `removing the current item and all after it ends - the first item current`() {
        queue.setQueue(listOf(a, b, c), null, 1)
        val (_, itemB, itemC) = queue.lists.base

        queue.remove(setOf(itemB.uid, itemC.uid)) shouldBe true

        queue.currentItem?.song shouldBe a
    }

    @Test
    fun `removing the current last item on repeat all wraps to the first`() {
        queue.setQueue(listOf(a, b, c), null, 2)
        queue.setRepeatMode(RepeatMode.All)

        queue.remove(setOf(queue.lists.base[2].uid)) shouldBe true

        queue.currentItem?.song shouldBe a
    }

    @Test
    fun `updated songs keep their items - and bump only the song data version`() {
        queue.setQueue(listOf(a, b), null, 0)
        val before = queue.queueStateFlow.value
        val renamed = b.copy(name = "Renamed")

        queue.updateSongs(mapOf(b.id to renamed))

        val after = queue.queueStateFlow.value
        after.items.map { it.uid } shouldBe before.items.map { it.uid }
        after.items[1].song shouldBe renamed
        after.songDataVersion shouldBe before.songDataVersion + 1
        after.contentVersion shouldBe before.contentVersion
    }
}
