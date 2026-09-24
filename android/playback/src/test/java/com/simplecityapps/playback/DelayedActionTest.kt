package com.simplecityapps.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [PlaybackService]'s foreground-notification and shutdown delays: one pending action each, cancelled on re-trigger. */
@OptIn(ExperimentalCoroutinesApi::class)
class DelayedActionTest {
    private val runs = mutableListOf<String>()

    private fun TestScope.delayedAction(): DelayedAction = DelayedAction(backgroundScope)

    @Test
    fun `runs the action once its delay has passed, not before`() = runTest {
        val action = delayedAction()

        action.schedule(150) { runs += "stop foreground" }
        advanceTimeBy(149)
        runs shouldBe emptyList()

        advanceTimeBy(1)
        runCurrent()
        runs shouldBe listOf("stop foreground")
    }

    @Test
    fun `scheduling again cancels the pending action and restarts the delay`() = runTest {
        val action = delayedAction()

        action.schedule(10_000) { runs += "first" }
        advanceTimeBy(9_000)
        action.schedule(10_000) { runs += "second" }
        advanceTimeBy(9_000)
        runCurrent()
        runs shouldBe emptyList()

        advanceTimeBy(1_000)
        runCurrent()
        runs shouldBe listOf("second")
    }

    @Test
    fun `a cancelled action never runs`() = runTest {
        val action = delayedAction()

        action.schedule(15_000) { runs += "shutdown" }
        advanceTimeBy(14_000)
        action.cancel()
        advanceTimeBy(10_000)
        runCurrent()

        runs shouldBe emptyList()
    }

    @Test
    fun `an action whose delay has passed but has not been dispatched is still cancelled`() = runTest {
        val action = delayedAction()

        action.schedule(150) { runs += "stop foreground" }
        // The delay has passed, so the action is due, but nothing has run it yet.
        advanceTimeBy(150)
        action.cancel()
        runCurrent()

        runs shouldBe emptyList()
    }

    @Test
    fun `cancelling the scope cancels the pending action`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val action = DelayedAction(scope)

        action.schedule(15_000) { runs += "shutdown" }
        scope.cancel()
        advanceTimeBy(20_000)
        runCurrent()

        runs shouldBe emptyList()
    }

    @Test
    fun `an action can be scheduled again after it has run`() = runTest {
        val action = delayedAction()

        action.schedule(150) { runs += "first" }
        advanceTimeBy(150)
        runCurrent()
        action.schedule(150) { runs += "second" }
        advanceTimeBy(150)
        runCurrent()

        runs shouldBe listOf("first", "second")
    }
}
