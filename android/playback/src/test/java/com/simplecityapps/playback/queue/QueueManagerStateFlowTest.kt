package com.simplecityapps.playback.queue

import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * QueueManager publishes the queue, shuffle mode and repeat mode as StateFlows. Every published queue
 * snapshot matches the getters at the moment it's published.
 */
class QueueManagerStateFlowTest {
    /** What changed in each published queue snapshot, and each repeat mode change, in order. */
    private val events = mutableListOf<String>()

    /** Snapshots that didn't match the getters when they were published. */
    private val mismatches = mutableListOf<QueueState>()
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))

    // Unconfined, so each value is recorded as it's published, while the getters still describe it.
    private val collectionScope = CoroutineScope(Dispatchers.Unconfined)

    init {
        collectionScope.launchCollectingChanges(queueManager.queueStateFlow, queueManager.queueStateFlow.value) { previous, current ->
            events += describe(previous, current)
            checkFlowMatchesGetters(current)
        }
        collectionScope.launchCollectingChanges(queueManager.repeatModeFlow, queueManager.repeatModeFlow.value) { _, current ->
            events += "repeatChanged $current"
        }
    }

    @After
    fun tearDown() {
        collectionScope.cancel()
    }

    private fun describe(
        previous: QueueState,
        current: QueueState
    ): String = buildList {
        if (current.shuffleMode != previous.shuffleMode) add("shuffleChanged ${current.shuffleMode}")
        if (current.contentVersion != previous.contentVersion) {
            add(if (current.nonMoveContentVersion == previous.nonMoveContentVersion) "queueChanged Move" else "queueChanged")
        }
        if (current.currentPosition != previous.currentPosition) add("positionChanged ${previous.currentPosition} -> ${current.currentPosition}")
        if (current.isRestored != previous.isRestored) add("restored ${current.isRestored}")
    }.joinToString(", ").ifEmpty { "republished" }

    private fun checkFlowMatchesGetters(state: QueueState) {
        val expected =
            state.copy(
                items = queueManager.getQueue().toList(),
                currentItem = queueManager.getCurrentItem(),
                currentPosition = queueManager.getCurrentPosition(),
                isRestored = queueManager.hasRestoredQueue,
                shuffleMode = queueManager.getShuffleMode()
            )
        if (state != expected) mismatches += state
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
    fun `setting the queue publishes items, then the current item`() = runTest {
        queueManager.setQueue(listOf(testSong(1), testSong(2), testSong(3)), position = 1)

        ids() shouldBe listOf(1L, 2L, 3L)
        queueManager.queueStateFlow.value.currentItem!!.song.id shouldBe 2L
        queueManager.queueStateFlow.value.currentPosition shouldBe 1
        events shouldBe listOf("queueChanged", "positionChanged null -> 1")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `moving an item publishes the new order`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.move(0, 2)

        ids() shouldBe listOf(2L, 3L, 1L)
        queueManager.queueStateFlow.value.currentPosition shouldBe 2
        events shouldBe listOf("queueChanged Move, positionChanged 0 -> 2")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `removing an item publishes the remaining items`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.remove(listOf(queueManager.getQueue()[2]))

        ids() shouldBe listOf(1L, 2L)
        events shouldBe listOf("queueChanged")
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
        events shouldBe listOf("queueChanged", "queueChanged")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `clearing settles on an empty queue with no current item`() = runTest {
        setQueueOf(1, 2)

        queueManager.clear()

        queueManager.queueStateFlow.value.items shouldBe QueueState.Empty.items
        queueManager.queueStateFlow.value.currentItem shouldBe QueueState.Empty.currentItem
        queueManager.queueStateFlow.value.currentPosition shouldBe QueueState.Empty.currentPosition
        // The change is published while the cleared item is still current, then the settled state.
        events shouldBe listOf("queueChanged, positionChanged 0 -> null", "republished")
        mismatches shouldBe emptyList()
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

        // Moving an item to its own position: a change is published, but the resulting items, uids,
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
        events.clear()

        queueManager.toggleShuffleMode()

        queueManager.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.On
        queueManager.queueStateFlow.value.items shouldBe queueManager.getQueue(QueueManager.ShuffleMode.On)
        queueManager.queueStateFlow.value.shuffleMode shouldBe QueueManager.ShuffleMode.On
        events.first() shouldBe "shuffleChanged On"
        events[1] shouldBe "queueChanged"
        mismatches shouldBe emptyList()

        queueManager.toggleShuffleMode()

        queueManager.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.Off
        queueManager.queueStateFlow.value.shuffleMode shouldBe QueueManager.ShuffleMode.Off
        ids() shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `the queue state only carries a new shuffle mode once the shuffled order is in place`() = runTest {
        setQueueOf(1, 2, 3)
        val published = mutableListOf<QueueState>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queueManager.queueStateFlow.collect { published += it }
        }

        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true)
        collection.cancel()

        val firstOn = published.first { it.shuffleMode == QueueManager.ShuffleMode.On }
        firstOn.items shouldBe queueManager.getQueue(QueueManager.ShuffleMode.On)
        firstOn.items.map { it.song.id }.sorted() shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `shuffle before the queue is restored still publishes the shuffled queue`() = runTest {
        setQueueOf(1, 2, 3)

        queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true)

        events shouldBe listOf("shuffleChanged On")
        mismatches shouldBe emptyList()
        queueManager.queueStateFlow.value.items shouldBe queueManager.getQueue(QueueManager.ShuffleMode.On)
        queueManager.queueStateFlow.value.currentPosition shouldBe queueManager.getCurrentPosition()
    }

    @Test
    fun `changing repeat mode publishes it, once per change`() {
        queueManager.toggleRepeatMode()
        queueManager.repeatModeFlow.value shouldBe QueueManager.RepeatMode.All

        queueManager.setRepeatMode(QueueManager.RepeatMode.One)
        queueManager.repeatModeFlow.value shouldBe QueueManager.RepeatMode.One

        queueManager.setRepeatMode(QueueManager.RepeatMode.One)

        events shouldBe listOf("repeatChanged All", "repeatChanged One")
        mismatches shouldBe emptyList()
    }

    @Test
    fun `a queue change bumps the content version, a non-move one bumps the non-move version, a position change bumps neither`() = runTest {
        setQueueOf(1, 2, 3)
        val afterSet = queueManager.queueStateFlow.value

        queueManager.skipTo(1)
        val afterSkip = queueManager.queueStateFlow.value
        afterSkip.contentVersion shouldBe afterSet.contentVersion
        afterSkip.nonMoveContentVersion shouldBe afterSet.nonMoveContentVersion

        queueManager.move(2, 0)
        val afterMove = queueManager.queueStateFlow.value
        afterMove.contentVersion shouldBe afterSet.contentVersion + 1
        afterMove.nonMoveContentVersion shouldBe afterSet.nonMoveContentVersion

        queueManager.addToQueue(listOf(testSong(4)))
        val afterAdd = queueManager.queueStateFlow.value
        afterAdd.contentVersion shouldBe afterMove.contentVersion + 1
        afterAdd.nonMoveContentVersion shouldBe afterMove.nonMoveContentVersion + 1
    }

    @Test
    fun `restoring the queue publishes it as restored`() = runTest {
        setQueueOf(1, 2)
        queueManager.queueStateFlow.value.isRestored shouldBe false

        queueManager.hasRestoredQueue = true

        queueManager.queueStateFlow.value.isRestored shouldBe true
        events shouldBe listOf("restored true")
        mismatches shouldBe emptyList()
    }
}
