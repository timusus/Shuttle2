package com.simplecityapps.shuttle.shared.downloads

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A path has one download at a time, even while its address is still being found (#933). */
class WantedDownloadsTest {
    private class Task(
        val id: Int
    )

    private class Probe {
        var cancelled = false
    }

    private val cancelled = mutableListOf<Int>()
    private val wanted = WantedDownloads<Task, Probe>(
        sameTask = { a, b -> a.id == b.id },
        cancelTask = { cancelled += it.id },
        cancelPending = { it.cancelled = true }
    )

    @Test
    fun anEarlierLaunchsTaskIsAdoptedWhenNothingElseIsWanted() {
        wanted.found("a", Task(1)) shouldBe true
        wanted.isRunning("a", Task(1)) shouldBe true
        cancelled.shouldBeEmpty()
    }

    @Test
    fun anEarlierLaunchsTaskFoundWhileTheAddressIsFoundIsCancelledNotAdopted() {
        val probe = Probe()
        wanted.pend("a", probe)
        wanted.found("a", Task(1)) shouldBe false
        cancelled shouldContainExactly listOf(1)
        // Its progress and outcome are not the path's, and the probe still makes the one task
        wanted.isWanted("a", Task(1)) shouldBe false
        wanted.resolved("a", probe) shouldBe true
        wanted.run("a", Task(2))
        wanted.isRunning("a", Task(2)) shouldBe true
        wanted.isWanted("a", Task(1)) shouldBe false
    }

    @Test
    fun anEarlierLaunchsTaskFoundAfterARestartIsCancelled() {
        wanted.run("a", Task(2))
        wanted.found("a", Task(1)) shouldBe false
        wanted.found("a", Task(2)) shouldBe false
        cancelled shouldContainExactly listOf(1)
    }

    @Test
    fun aRestartDuringTheProbeCancelsItAndItsAnswerMakesNoTask() {
        val first = Probe()
        wanted.pend("a", first)
        val second = Probe()
        wanted.pend("a", second)
        first.cancelled shouldBe true
        wanted.resolved("a", first) shouldBe false
        wanted.resolved("a", second) shouldBe true
    }

    @Test
    fun aRemovalDuringTheProbeCancelsIt() {
        val probe = Probe()
        wanted.pend("a", probe)
        wanted.clear("a")
        probe.cancelled shouldBe true
        wanted.resolved("a", probe) shouldBe false
    }

    @Test
    fun aStartWithAProbeCancelsTheRunningTask() {
        wanted.run("a", Task(1))
        wanted.pend("a", Probe())
        cancelled shouldContainExactly listOf(1)
        wanted.isRunning("a", Task(1)) shouldBe false
    }

    @Test
    fun aTaskNobodyClaimedYetIsStillWantedButNotRunning() {
        wanted.isWanted("a", Task(1)) shouldBe true
        wanted.isRunning("a", Task(1)) shouldBe false
        wanted.finished("a") shouldBe false
    }

    @Test
    fun aFinishedTaskIsForgotten() {
        wanted.run("a", Task(1))
        wanted.finished("a") shouldBe true
        wanted.isRunning("a", Task(1)) shouldBe false
        cancelled.shouldBeEmpty()
    }
}
