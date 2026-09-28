package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ServerSessionTest {
    private val strings =
        object : ServerStrings {
            override val addressMissing = "No address"
            override val authenticationError = "Sign-in failed"
            override val unknownName = "Unknown"
        }
    private val authenticatedAt = mutableListOf<String>()

    private val querying = Event.Progress(MessageProgress(ImportPhase.Connecting, progress = null))

    private fun session(
        address: String?,
        credentials: String?
    ) = withServerSession<String, String>(
        strings = strings,
        address = address,
        authenticate = {
            authenticatedAt += it
            credentials
        }
    ) { address, credentials -> emit(FlowEvent.Success("$address as $credentials")) }

    @Test
    fun `no address fails without signing in`() = runTest {
        session(address = null, credentials = "token").toList().described() shouldBe listOf(Event.Failure(strings.addressMissing))
        authenticatedAt shouldBe emptyList()
    }

    @Test
    fun `reports progress - signs in and runs the body with the session`() = runTest {
        session(address = "https://server", credentials = "token").toList().described() shouldBe listOf(
            querying,
            Event.Success("https://server as token")
        )
        authenticatedAt shouldBe listOf("https://server")
    }

    @Test
    fun `a failed sign-in fails the sync`() = runTest {
        session(address = "https://server", credentials = null).toList().described() shouldBe listOf(
            querying,
            Event.Failure(strings.authenticationError)
        )
    }
}
