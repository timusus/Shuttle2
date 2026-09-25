package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    private val queueManager = FakeQueueManager()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun queueOf(vararg names: String): QueueState {
        val items = names.mapIndexed { index, name -> QueueItem(uid = index.toLong(), song = createSong(id = index.toLong(), name = name), isCurrent = index == 0) }
        return QueueState.Empty.copy(items = items, currentItem = items.firstOrNull(), isRestored = true)
    }

    @Test
    fun `an unrestored empty queue is unknown, so the saved level stands`() {
        QueueState.Empty.toShellQueueUiState().hasQueue shouldBe null
    }

    @Test
    fun `a restored empty queue has no queue`() {
        QueueState.Empty.copy(isRestored = true).toShellQueueUiState().hasQueue shouldBe false
    }

    @Test
    fun `items map to rows with the current one marked`() {
        val state = queueOf("One", "Two").toShellQueueUiState()
        state.hasQueue shouldBe true
        state.items.map { it.title } shouldBe listOf("One", "Two")
        state.current?.title shouldBe "One"
    }

    @Test
    fun `the view model follows the queue`() = runTest {
        val viewModel = ShellViewModel(queueManager)
        val collected = mutableListOf<Boolean?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.queue.collect { collected += it.hasQueue } }

        queueManager.queueStateFlow.value = queueOf("One")
        queueManager.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)

        collected shouldBe listOf(null, true, false)
        viewModel.queue.first().hasQueue shouldBe false
        job.cancel()
    }
}
