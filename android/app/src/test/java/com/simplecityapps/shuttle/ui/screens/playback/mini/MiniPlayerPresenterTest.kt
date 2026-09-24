package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.clone
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test

class MiniPlayerPresenterTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playbackManager = FakePlaybackManager()
    private val queueManager = FakeQueueManager()
    private val presenter = MiniPlayerPresenter(playbackManager, queueManager)
    private val view = RecordingView()

    private val song = createSong(id = 1, name = "Come Together", duration = 200_000)

    @Test
    fun `binding renders the current state once`() {
        setCurrent(song)

        presenter.bindView(view)

        view.events shouldBe listOf("progress 0/200000", "song Come Together", "state Paused")
    }

    @Test
    fun `playback state changes reach the view`() {
        presenter.bindView(view)
        view.events.clear()

        playbackManager.playbackStateFlow.value = PlaybackState.Playing

        view.events shouldBe listOf("state Playing")
    }

    @Test
    fun `progress changes reach the view`() {
        presenter.bindView(view)
        view.events.clear()

        playbackManager.progressFlow.value = PlaybackProgress(position = 5_000, duration = 200_000)

        view.events shouldBe listOf("progress 5000/200000")
    }

    @Test
    fun `a new current item reaches the view, and a queue change that keeps it does not`() {
        presenter.bindView(view)
        view.events.clear()

        setCurrent(song)
        val state = queueManager.queueStateFlow.value
        queueManager.queueStateFlow.value = state.copy(items = state.items + createSong(id = 2).toQueueItem(false), contentVersion = 1)

        view.events shouldBe listOf("song Come Together")
    }

    @Test
    fun `editing the current song's data reaches the view`() {
        setCurrent(song)
        presenter.bindView(view)
        view.events.clear()

        val edited = song.copy(name = "New Name")
        val state = queueManager.queueStateFlow.value
        queueManager.queueStateFlow.value = state.copy(currentItem = state.currentItem!!.clone(song = edited), songDataVersion = state.songDataVersion + 1)

        view.events shouldBe listOf("song New Name")
    }

    @Test
    fun `changes made after the initial draw but before collection starts still reach the view`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        setCurrent(song)

        presenter.bindView(view)
        view.events.clear()

        // Collection is launched but hasn't run yet, so these land between the draw and its first read.
        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        playbackManager.progressFlow.value = PlaybackProgress(position = 5_000, duration = 200_000)
        setCurrent(createSong(id = 2, name = "Something"))
        dispatcher.scheduler.advanceUntilIdle()

        view.events shouldContainExactlyInAnyOrder listOf("state Playing", "progress 5000/200000", "song Something")
    }

    @Test
    fun `nothing reaches the view after unbinding`() {
        presenter.bindView(view)
        presenter.unbindView()
        view.events.clear()

        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        setCurrent(song)

        view.events shouldBe emptyList()
    }

    private fun setCurrent(song: Song) {
        val item = song.toQueueItem(isCurrent = true)
        queueManager.queueStateFlow.value = QueueState(items = listOf(item), currentItem = item, currentPosition = 0)
    }

    private class RecordingView : MiniPlayerContract.View {
        val events = mutableListOf<String>()

        override fun setPlaybackState(playbackState: PlaybackState) {
            events += "state $playbackState"
        }

        override fun setCurrentSong(song: Song?) {
            events += "song ${song?.name}"
        }

        override fun setProgress(
            position: Int,
            duration: Int
        ) {
            events += "progress $position/$duration"
        }
    }
}
