package com.simplecityapps.shuttle.ui.actions

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class ConfirmationHandoffTest {

    private val handoff = ConfirmationHandoff<String>(pickupTimeout = 5.seconds, answerTimeout = 5.minutes)

    @Test
    fun `a host launches the request and the user's answer completes it`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()

        request.payload shouldBe "delete"
        handoff.launch(request) shouldBe true
        handoff.deliver(request.token, true)

        confirmed.await() shouldBe true
    }

    @Test
    fun `an answer delivered to a recreated host still completes the request`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.launch(request)

        // The recreated host never saw the request; it kept only the token
        val savedToken = request.token
        handoff.deliver(savedToken, false)

        confirmed.await() shouldBe false
    }

    @Test
    fun `a launched request isn't offered to another host`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.launch(request)

        handoff.launch(request) shouldBe false
        withTimeoutOrNull(1.seconds) { handoff.requests.first() } shouldBe null

        handoff.deliver(request.token, true)
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
    fun `a launched request waits minutes for the user`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.launch(request)

        advanceTimeBy(4.minutes)
        confirmed.isCompleted shouldBe false

        handoff.deliver(request.token, true)
        confirmed.await() shouldBe true
    }

    @Test
    fun `a launched request whose answer never comes is declined after the cap and lets the next caller through`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }
        val firstRequest = handoff.requests.first()
        handoff.launch(firstRequest)

        advanceTimeBy(5.minutes + 1.seconds)

        first.await() shouldBe false
        handoff.requests.first().payload shouldBe "second"
    }

    @Test
    fun `an answer arriving after the cap is ignored`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }
        val firstRequest = handoff.requests.first()
        handoff.launch(firstRequest)
        advanceTimeBy(5.minutes + 1.seconds)
        val secondRequest = handoff.requests.first()
        handoff.launch(secondRequest)

        handoff.deliver(firstRequest.token, true)
        second.isCompleted shouldBe false

        handoff.deliver(secondRequest.token, false)
        first.await() shouldBe false
        second.await() shouldBe false
    }

    @Test
    fun `abandoning a launched request declines it and lets the next caller through`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }
        val firstRequest = handoff.requests.first()
        handoff.launch(firstRequest)

        handoff.abandon(firstRequest.token)

        first.await() shouldBe false
        handoff.requests.first().payload shouldBe "second"
        second.isCompleted shouldBe false
    }

    @Test
    fun `a host can't abandon or answer a request it didn't launch`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.launch(request)

        handoff.abandon(request.token + 1)
        handoff.deliver(request.token + 1, true)
        runCurrent()
        confirmed.isCompleted shouldBe false

        handoff.deliver(request.token, true)
        confirmed.await() shouldBe true
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
    fun `an answer before the request is launched is ignored`() = runTest {
        val confirmed = async { handoff.confirm("delete") }
        val request = handoff.requests.first()
        handoff.deliver(request.token, true)
        confirmed.isCompleted shouldBe false

        handoff.launch(request)
        handoff.deliver(request.token, false)
        confirmed.await() shouldBe false
    }

    @Test
    fun `a second caller waits for the first request to finish`() = runTest {
        val first = async { handoff.confirm("first") }
        val second = async { handoff.confirm("second") }

        val firstRequest = handoff.requests.first()
        firstRequest.payload shouldBe "first"
        handoff.launch(firstRequest)
        handoff.deliver(firstRequest.token, true)
        first.await() shouldBe true

        val secondRequest = handoff.requests.first()
        secondRequest.payload shouldBe "second"
        handoff.launch(secondRequest)
        handoff.deliver(secondRequest.token, false)
        second.await() shouldBe false
    }
}
