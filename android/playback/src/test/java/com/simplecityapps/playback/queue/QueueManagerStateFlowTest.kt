package com.simplecityapps.playback.queue

import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * QueueManager publishes the queue, shuffle mode and repeat mode as StateFlows, set just before
 * the matching [QueueChangeCallback] is dispatched, while the callbacks keep firing as before.
 */
class QueueManagerStateFlowTest {
    private val events = mutableListOf<String>()

    /** The queue flow value each queue/position callback saw, compared against the getters. */
    private val mismatches = mutableListOf<String>()

    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))

    init {
        queueWatcher.addCallback(
            object : QueueChangeCallback {
                override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
                    events += "queueChanged $reason"
                    checkFlowMatchesGetters("onQueueChanged")
                }

                override fun onQueuePositionChanged(
                    oldPosition: Int?,
                    newPosition: Int?
                ) {
                    events += "positionChanged $oldPosition -> $newPosition"
                    checkFlowMatchesGetters("onQueuePositionChanged")
                }

                override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
                    events += "shuffleChanged $shuffleMode"
                    if (queueManager.shuffleModeFlow.value != shuffleMode) mismatches += "onShuffleChanged"
                }

                override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
                    events += "repeatChanged $repeatMode"
                    if (queueManager.repeatModeFlow.value != repeatMode) mismatches += "onRepeatChanged"
                }
            }
        )
    }

    private fun checkFlowMatchesGetters(callback: String) {
        val expected =
            QueueState(
                items = queueManager.getQueue().toList(),
                currentItem = queueManager.getCurrentItem(),
                currentPosition = queueManager.getCurrentPosition(),
                version = queueManager.queueStateFlow.value.version
            )
        if (queueManager.queueStateFlow.value != expected) mismatches += callback
    }

    private fun ids() = queueManager.queueStateFlow.value.items.map { it.song.id }

    private suspend fun setQueueOf(vararg ids: Long) {
        queueManager.setQueue(ids.map { testSong(it) })
        events.clear()
    }

    @Test
    fun `initial values are an empty queue with shuffle and repeat off`() {
        queueManager.queueStateFlow.value shouldBe QueueState.Empty
        queueManager.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.Off
        queueManager.repeatModeFlow.value shouldBe QueueManager.RepeatMode.Off
    }

    @Test
    fun `setting the queue publishes items and current item, and the callbacks still fire`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)), position = 1)

        ids() shouldBe listOf(1L, 2L, 3L)
        queueManager.queueStateFlow.value.currentItem!!.song.id shouldBe 2L
        queueManager.queueStateFlow.value.currentPosition shouldBe 1
        events shouldBe listOf("queueChanged Unknown", "positionChanged null -> 1")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `moving an item publishes the new order`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.move(0, 2)

        ids() shouldBe listOf(2L, 3L, 1L)
        queueManager.queueStateFlow.value.currentPosition shouldBe 2
        events shouldBe listOf("queueChanged Move", "positionChanged 0 -> 2")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `removing an item publishes the remaining items`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.remove(listOf(queueManager.getQueue()[2]))

        ids() shouldBe listOf(1L, 2L)
        events shouldBe listOf("queueChanged Unknown")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `skipping publishes the new current item`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.skipToNext()

        queueManager.queueStateFlow.value.currentItem!!.song.id shouldBe 2L
        queueManager.queueStateFlow.value.currentPosition shouldBe 1
        events shouldBe listOf("positionChanged 0 -> 1")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `adding songs publishes them`() = runTest {
        setQueueOf(1, 2)

        queueManager.addToQueue(listOf(testSong(3)))
        queueManager.addToNext(listOf(testSong(4)))

        ids() shouldBe listOf(1L, 4L, 2L, 3L)
        events shouldBe listOf("queueChanged Unknown", "queueChanged Unknown")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `clearing settles on an empty queue with no current item`() = runTest {
        setQueueOf(1, 2)

        queueManager.clear()

        queueManager.queueStateFlow.value.items shouldBe QueueState.Empty.items
        queueManager.queueStateFlow.value.currentItem shouldBe QueueState.Empty.currentItem
        queueManager.queueStateFlow.value.currentPosition shouldBe QueueState.Empty.currentPosition
        events shouldBe listOf("queueChanged Unknown")
    }

    @Test
    fun `a published queue is a snapshot`() = runTest {
        setQueueOf(1, 2)
        val published = queueManager.queueStateFlow.value

        queueManager.addToQueue(listOf(testSong(3)))

        published.items.map { it.song.id } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `a queue change that leaves items, current item and position unchanged still emits a new snapshot`() = runTest {
        setQueueOf(1, 2, 3)
        val before = queueManager.queueStateFlow.value

        // Moving an item to its own position: the callback fires, but the resulting items, uids,
        // current item and position all match the previous snapshot exactly.
        queueManager.move(0, 0)
        val after = queueManager.queueStateFlow.value

        after shouldNotBe before
        after.items.map { it.uid } shouldBe before.items.map { it.uid }
        after.currentItem shouldBe before.currentItem
        after.currentPosition shouldBe before.currentPosition
        events shouldBe listOf("queueChanged Move")
    }

    @Test
    fun `toggling shuffle publishes the mode and the shuffled queue`() = runTest {
        setQueueOf(1, 2, 3)
        queueManager.hasRestoredQueue = true

        queueManager.toggleShuffleMode()

        queueManager.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.On
        queueManager.queueStateFlow.value.items shouldBe queueManager.getQueue(QueueManager.ShuffleMode.On)
        events.first() shouldBe "shuffleChanged On"
        events[1] shouldBe "queueChanged Unknown"
        mismatches shouldBe emptyList()

        queueManager.toggleShuffleMode()

        queueManager.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.Off
        ids() shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `shuffle before the queue is restored still publishes the shuffled queue`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true)

        events shouldBe listOf("shuffleChanged On")
        queueManager.queueStateFlow.value.items shouldBe queueManager.getQueue(QueueManager.ShuffleMode.On)
        queueManager.queueStateFlow.value.currentPosition shouldBe queueManager.getCurrentPosition()
    }

    @Test
    fun `changing repeat mode publishes it and the callback still fires`() {
        queueManager.toggleRepeatMode()
        queueManager.repeatModeFlow.value shouldBe QueueManager.RepeatMode.All

        queueManager.setRepeatMode(QueueManager.RepeatMode.One)
        queueManager.repeatModeFlow.value shouldBe QueueManager.RepeatMode.One

        queueManager.setRepeatMode(QueueManager.RepeatMode.One)

        events shouldBe listOf("repeatChanged All", "repeatChanged One")
        mismatches shouldBe emptyList()
    }
}
