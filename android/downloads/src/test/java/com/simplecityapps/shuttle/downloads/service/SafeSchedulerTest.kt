package com.simplecityapps.shuttle.downloads.service

import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import io.kotest.matchers.shouldBe
import org.junit.Test

class SafeSchedulerTest {
    private val requirements = Requirements(Requirements.NETWORK_UNMETERED)

    @Test
    fun `a JobScheduler failure degrades to not scheduled instead of crashing`() {
        val scheduler = SafeScheduler(ThrowingScheduler())

        scheduler.schedule(requirements, "com.simplecityapps.shuttle", "action") shouldBe false
        scheduler.cancel() shouldBe false
        scheduler.getSupportedRequirements(requirements) shouldBe requirements
    }

    @Test
    fun `a working scheduler is passed through`() {
        val scheduler = SafeScheduler(WorkingScheduler())

        scheduler.schedule(requirements, "com.simplecityapps.shuttle", "action") shouldBe true
        scheduler.cancel() shouldBe true
    }
}

private class ThrowingScheduler : Scheduler {
    override fun schedule(
        requirements: Requirements,
        servicePackage: String,
        serviceAction: String
    ): Boolean = throw IllegalArgumentException("No such service ComponentInfo{…}")

    override fun cancel(): Boolean = throw IllegalArgumentException("No such service ComponentInfo{…}")

    override fun getSupportedRequirements(requirements: Requirements): Requirements = throw IllegalArgumentException("No such service ComponentInfo{…}")
}

private class WorkingScheduler : Scheduler {
    override fun schedule(
        requirements: Requirements,
        servicePackage: String,
        serviceAction: String
    ): Boolean = true

    override fun cancel(): Boolean = true

    override fun getSupportedRequirements(requirements: Requirements): Requirements = requirements
}
