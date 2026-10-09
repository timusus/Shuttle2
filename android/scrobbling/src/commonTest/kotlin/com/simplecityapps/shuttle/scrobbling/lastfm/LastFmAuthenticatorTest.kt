package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.scrobbling.LastFmSignInResult
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

class LastFmAuthenticatorTest {
    private val dao = FakeScrobbleDao()
    private val scrobbleQueue = ScrobbleQueue(dao, FakeScrobbleFlushScheduler())
    private val server = FakeLastFmServer()
    private val sessionStore = FakeLastFmSessionStore()

    private fun authenticator(credentials: LastFmCredentials = testCredentials) = LastFmAuthenticator(server.client(credentials), sessionStore, scrobbleQueue)

    @Test
    fun `a build without an API key is unavailable`() = runTest {
        authenticator(LastFmCredentials(apiKey = "", sharedSecret = "")).state.first() shouldBe LastFmAccountState.Unavailable
    }

    @Test
    fun `starts signed out`() = runTest {
        authenticator().state.first() shouldBe LastFmAccountState.SignedOut
    }

    @Test
    fun `starting sign-in keeps the token and returns the approval page`() = runTest {
        server.enqueue("""{"token":"tok"}""")
        val authenticator = authenticator()

        val url = authenticator.startSignIn()

        url shouldBe "https://www.last.fm/api/auth/?api_key=api-key&token=tok"
        sessionStore.pendingToken.value shouldBe "tok"
        authenticator.state.first() shouldBe LastFmAccountState.AwaitingApproval
        server.form()["method"] shouldBe "auth.getToken"
        server.form()["api_sig"]?.length shouldBe 32
    }

    @Test
    fun `starting sign-in while offline returns nothing and keeps no token`() = runTest {
        server.enqueueOffline()

        authenticator().startSignIn() shouldBe null
        sessionStore.pendingToken.value shouldBe null
    }

    @Test
    fun `finishing without starting is not started`() = runTest {
        authenticator().finishSignIn() shouldBe LastFmSignInResult.NotStarted
        server.requests shouldBe emptyList()
    }

    @Test
    fun `an approved token becomes the session and clears the token`() = runTest {
        sessionStore.savePendingToken("tok")
        server.enqueue("""{"session":{"name":"tim","key":"sk"}}""")
        val authenticator = authenticator()

        authenticator.finishSignIn() shouldBe LastFmSignInResult.SignedIn

        sessionStore.session.value shouldBe LastFmSession(key = "sk", username = "tim")
        sessionStore.pendingToken.value shouldBe null
        authenticator.state.first() shouldBe LastFmAccountState.SignedIn("tim")
        server.form()["method"] shouldBe "auth.getSession"
        server.form()["token"] shouldBe "tok"
    }

    @Test
    fun `a token that is not approved yet keeps waiting`() = runTest {
        sessionStore.savePendingToken("tok")
        server.enqueue("""{"error":14,"message":"Unauthorized Token"}""", HttpStatusCode.Forbidden)

        authenticator().finishSignIn() shouldBe LastFmSignInResult.NotApproved

        sessionStore.pendingToken.value shouldBe "tok"
        sessionStore.session.value shouldBe null
    }

    @Test
    fun `an expired token is dropped so sign-in can start again`() = runTest {
        sessionStore.savePendingToken("tok")
        server.enqueue("""{"error":15,"message":"expired"}""", HttpStatusCode.Forbidden)

        authenticator().finishSignIn() shouldBe LastFmSignInResult.Expired

        sessionStore.pendingToken.value shouldBe null
    }

    @Test
    fun `being offline while finishing fails but keeps waiting`() = runTest {
        sessionStore.savePendingToken("tok")
        server.enqueueOffline()

        authenticator().finishSignIn() shouldBe LastFmSignInResult.Failed

        sessionStore.pendingToken.value shouldBe "tok"
    }

    @Test
    fun `signing out forgets the session and drops the queued scrobbles`() = runTest {
        sessionStore.signIn(LastFmSession(key = "sk", username = "tim"))
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)

        authenticator().signOut()

        sessionStore.session.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `the same account signing back in after a forced sign-out keeps its queue`() = runTest {
        sessionStore.signIn(LastFmSession(key = "old", username = "tim"))
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        sessionStore.signOut()
        sessionStore.savePendingToken("tok")
        server.enqueue("""{"session":{"name":"tim","key":"sk"}}""")

        authenticator().finishSignIn() shouldBe LastFmSignInResult.SignedIn

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `a different account signing in after a forced sign-out does not inherit the queue`() = runTest {
        sessionStore.signIn(LastFmSession(key = "old", username = "tim"))
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        sessionStore.signOut()
        sessionStore.savePendingToken("tok")
        server.enqueue("""{"session":{"name":"someone-else","key":"sk"}}""")

        authenticator().finishSignIn() shouldBe LastFmSignInResult.SignedIn

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }
}
