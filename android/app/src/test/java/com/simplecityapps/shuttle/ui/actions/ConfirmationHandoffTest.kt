package com.simplecityapps.shuttle.ui.actions

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class ConfirmationHandoffTest {

    private val handoff = ConfirmationHandoff<String>(pickupTimeout = 5.seconds)

    @Test
    fun `a host launches the request and the user's answer completes it`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()

        request.payload shouldBe "delete"
        handoff.launch(request) shouldBe true
        handoff.deliver(true)

        confirmed.await() shouldBe true
    }

    @Test
    fun `an answer delivered to a recreated host still completes the request`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        handoff.launch(handoff.requests.first())

        // The recreated host never saw the request; the handoff knows which one is waiting
        handoff.deliver(false)

        confirmed.await() shouldBe false
    }

    @Test
    fun `a launched request isn't offered to another host`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.launch(request)

        handoff.launch(request) shouldBe false
        withTimeoutOrNull(1.seconds) { handoff.requests.first() } shouldBe null

        handoff.deliver(true)
        confirmed.await() shouldBe true
    }

    @Test
    fun `a request no host picks up fails after the pickup timeout`() = runTest {
        val confirmed = async { handoff.confirm("delete") }

        advanceTimeBy(5.seconds + 1.seconds)

        confirmed.isCompleted shouldBe true
        confirmed.await() shouldBe false
        withTimeoutOrNull(1.seconds) { handoff.requests.first() } shouldBe null
    }

    @Test
    fun `a launched request waits for the user however long they take`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        handoff.launch(handoff.requests.first())

        advanceTimeBy(60.seconds)
        confirmed.isCompleted shouldBe false

        handoff.deliver(true)
        confirmed.await() shouldBe true
    }

    @Test
    fun `abandoning a launched request declines it and lets the next caller through`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }
        handoff.launch(handoff.requests.first())

        handoff.abandon()

        first.await() shouldBe false
        handoff.requests.first().payload shouldBe "second"
        second.isCompleted shouldBe false
    }

    @Test
    fun `a cancelled caller withdraws its request so no later host launches it`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()

        confirmed.cancel()
        runCurrent()

        handoff.launch(request) shouldBe false
        withTimeoutOrNull(1.seconds) { handoff.requests.first() } shouldBe null
    }

    @Test
    fun `an answer with no launched request is ignored`() = runTest {
        handoff.deliver(true)

        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.deliver(true)
        confirmed.isCompleted shouldBe false

        handoff.launch(request)
        handoff.deliver(false)
        confirmed.await() shouldBe false
    }

    @Test
    fun `a second caller waits for the first request to finish`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }

        val firstRequest = handoff.requests.first()
        firstRequest.payload shouldBe "first"
        handoff.launch(firstRequest)
        handoff.deliver(true)
        first.await() shouldBe true

        val secondRequest = handoff.requests.first()
        secondRequest.payload shouldBe "second"
        handoff.launch(secondRequest)
        handoff.deliver(false)
        second.await() shouldBe false
    }
}
