package com.simplecityapps.playback.mediasession

import com.simplecityapps.playback.queue.QueueState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A request to play something waits for the saved queue to be restored, but not for ever. */
@OptIn(ExperimentalCoroutinesApi::class)
class AwaitRestoredTest {
    private val queueState = MutableStateFlow(QueueState.Empty)

    @Test
    fun `waits until the queue is restored`() = runTest {
        var done = false
        launch {
            queueState.awaitRestored()
            done = true
        }
        advanceTimeBy(1_000)
        done shouldBe false

        queueState.value = queueState.value.copy(isRestored = true)
        runCurrent()

        done shouldBe true
        currentTime shouldBe 1_000
    }

    @Test
    fun `goes ahead once the wait times out, when the restore never finishes`() = runTest {
        queueState.awaitRestored()

        currentTime shouldBe RESTORE_WAIT_MS
    }

    @Test
    fun `returns straight away when the queue is already restored`() = runTest {
        queueState.value = queueState.value.copy(isRestored = true)

        queueState.awaitRestored()

        currentTime shouldBe 0
    }
}
