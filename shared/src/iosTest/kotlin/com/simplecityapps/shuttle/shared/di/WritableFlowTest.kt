package com.simplecityapps.shuttle.shared.di

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class WritableFlowTest {
    @Test
    fun `a collector that starts late gets the latest value`() = runTest {
        val flow = WritableFlow<String>()
        flow.emit("a")
        flow.emit("b")

        flow.first() shouldBe "b"
    }

    @Test
    fun `a collector gets each value emitted while it collects`() = runTest {
        val flow = WritableFlow<String>()
        val values = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { flow.take(2).toList(values) }

        flow.emit("a")
        flow.emit("b")
        job.join()

        values shouldBe listOf("a", "b")
    }
}
