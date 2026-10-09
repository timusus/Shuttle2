package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.scrobbling.ListenBrainzAccountState
import com.simplecityapps.shuttle.scrobbling.ListenBrainzSignInResult
import com.simplecityapps.shuttle.scrobbling.createSong
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ListenBrainzAuthenticatorTest {
    private val dao = FakeScrobbleDao()
    private val scrobbleQueue = ScrobbleQueue(dao, FakeScrobbleFlushScheduler())
    private val server = FakeListenBrainzServer()
    private val store = FakeListenBrainzSessionStore()
    private val authenticator = ListenBrainzAuthenticator(server.client(), store, scrobbleQueue)

    private val valid = """{"code":200,"valid":true,"user_name":"tim"}"""

    @Test
    fun `starts signed out`() = runTest {
        authenticator.state.first() shouldBe ListenBrainzAccountState.SignedOut
    }

    @Test
    fun `a valid token signs in and is stored trimmed`() = runTest {
        server.enqueue(valid)

        authenticator.signIn("  abc \n") shouldBe ListenBrainzSignInResult.SignedIn

        store.account.value shouldBe ListenBrainzAccount(token = "abc", username = "tim")
        authenticator.state.first() shouldBe ListenBrainzAccountState.SignedIn("tim")
    }

    @Test
    fun `an invalid token is not stored`() = runTest {
        server.enqueue("""{"code":200,"valid":false}""")

        authenticator.signIn("abc") shouldBe ListenBrainzSignInResult.InvalidToken

        store.account.value shouldBe null
    }

    @Test
    fun `a blank token is invalid without asking ListenBrainz`() = runTest {
        authenticator.signIn("   ") shouldBe ListenBrainzSignInResult.InvalidToken

        server.requests shouldBe emptyList()
    }

    @Test
    fun `offline or a server error fails the sign-in`() = runTest {
        server.enqueueOffline()
        server.enqueue("""{}""", HttpStatusCode.InternalServerError)

        authenticator.signIn("abc") shouldBe ListenBrainzSignInResult.Failed
        authenticator.signIn("abc") shouldBe ListenBrainzSignInResult.Failed
        store.account.value shouldBe null
    }

    @Test
    fun `signing out forgets the token and drops only the ListenBrainz queue`() = runTest {
        server.enqueue(valid)
        authenticator.signIn("abc")
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)

        authenticator.signOut()

        store.account.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `the same account signing back in after a forced sign-out keeps its queue`() = runTest {
        store.signIn(ListenBrainzAccount("old", "tim"))
        store.signOut()
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        server.enqueue(valid)

        authenticator.signIn("new")

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }

    @Test
    fun `a different account signing in after a forced sign-out does not inherit the queue`() = runTest {
        store.signIn(ListenBrainzAccount("old", "someone-else"))
        store.signOut()
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        server.enqueue(valid)

        authenticator.signIn("new")

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }
}
