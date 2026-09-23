package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueChangeCallback.QueueChangeReason
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

class QueuePresenterTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val queueManager = FakeQueueManager()
    private val presenter = QueuePresenter(queueManager, FakePlaybackManager(), FakeSongRepository())
    private val view = RecordingView()

    private val items = (1L..3L).map { id -> createSong(id = id).toQueueItem(isCurrent = id == 1L) }

    @Test
    fun `binding renders the queue once and scrolls to the current item`() {
        queueManager.queueStateFlow.value = QueueState(items, currentItem = items[0], currentPosition = 0, isRestored = true)

        presenter.bindView(view)

        view.events shouldBe listOf("clear", "data 3", "position 0/3", "scroll 0 forced")
    }

    @Test
    fun `a queue change rebuilds the list and forces the scroll`() {
        bindWithQueue()

        publish(items + createSong(id = 4).toQueueItem(false), contentChange = QueueChangeReason.Unknown)

        view.events shouldBe listOf("clear", "data 4", "position 0/4", "scroll 0 forced", "empty false")
    }

    @Test
    fun `a move updates the list in place without forcing the scroll`() {
        bindWithQueue()

        publish(listOf(items[0], items[2], items[1]), contentChange = QueueChangeReason.Move)

        view.events shouldBe listOf("data 3", "position 0/3", "scroll 0", "empty false")
    }

    @Test
    fun `a position change refreshes the current item without forcing the scroll`() {
        bindWithQueue()

        val next = items[1]
        queueManager.queueStateFlow.value = queueManager.queueStateFlow.value.copy(currentItem = next, currentPosition = 1)

        view.events shouldBe listOf("data 3", "position 1/3", "scroll 1")
    }

    @Test
    fun `the restore rebuilds the list and forces the scroll`() {
        queueManager.queueStateFlow.value = QueueState(items, currentItem = items[0], currentPosition = 0, isRestored = false)
        presenter.bindView(view)
        view.events.clear()

        queueManager.queueStateFlow.value = queueManager.queueStateFlow.value.copy(isRestored = true)

        view.events shouldBe listOf("clear", "data 3", "position 0/3", "scroll 0 forced", "empty false")
    }

    private fun bindWithQueue() {
        queueManager.queueStateFlow.value = QueueState(items, currentItem = items[0], currentPosition = 0, isRestored = true)
        presenter.bindView(view)
        view.events.clear()
    }

    private fun publish(
        newItems: List<QueueItem>,
        contentChange: QueueChangeReason
    ) {
        val state = queueManager.queueStateFlow.value
        queueManager.queueStateFlow.value = state.copy(items = newItems, contentVersion = state.contentVersion + 1, lastChangeReason = contentChange)
    }

    private class RecordingView : QueueContract.View {
        val events = mutableListOf<String>()

        override fun setData(
            queue: List<QueueItem>,
            progress: Float,
            playbackState: PlaybackState
        ) {
            events += "data ${queue.size}"
        }

        override fun toggleEmptyView(empty: Boolean) {
            events += "empty $empty"
        }

        override fun toggleLoadingView(loading: Boolean) {}

        override fun setQueuePosition(
            position: Int?,
            total: Int
        ) {
            events += "position $position/$total"
        }

        override fun showLoadError(error: Error) {}

        override fun scrollToPosition(
            position: Int?,
            forceScrollUpdate: Boolean
        ) {
            events += if (forceScrollUpdate) "scroll $position forced" else "scroll $position"
        }

        override fun showTagEditor(songs: List<Song>) {}

        override fun clearData() {
            events += "clear"
        }
    }
}
