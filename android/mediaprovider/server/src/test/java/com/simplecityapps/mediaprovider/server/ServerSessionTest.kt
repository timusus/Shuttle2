package com.simplecityapps.mediaprovider.server

import android.content.Context
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.R
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServerSessionTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val authenticatedAt = mutableListOf<String>()

    private val querying = Event.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), null))

    private fun session(
        address: String?,
        credentials: String?
    ) = withServerSession<String, String>(
        context = context,
        address = address,
        authenticate = {
            authenticatedAt += it
            credentials
        }
    ) { address, credentials -> emit(FlowEvent.Success("$address as $credentials")) }

    @Test
    fun `no address fails without signing in`() = runTest {
        session(address = null, credentials = "token").toList().described() shouldBe listOf(Event.Failure(context.getString(R.string.media_provider_address_missing)))
        authenticatedAt shouldBe emptyList()
    }

    @Test
    fun `reports progress, signs in and runs the body with the session`() = runTest {
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
            Event.Failure(context.getString(R.string.media_provider_authentication_error))
        )
    }
}
