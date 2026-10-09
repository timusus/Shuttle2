package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.scrobbling.createSong
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ListenBrainzScrobblerTest {
    private val dao = FakeScrobbleDao()
    private val server = FakeListenBrainzServer()
    private val store = FakeListenBrainzSessionStore(ListenBrainzAccount(token = "abc", username = "tim"))
    private val scrobbler = ListenBrainzScrobbler(server.client(), store, ScrobbleQueue(dao, FakeScrobbleFlushScheduler()))
    private val song = createSong(id = 1, duration = 200_000)

    @Test
    fun `now playing is sent with the token and never queued`() = runTest {
        scrobbler.nowPlaying(song)

        server.requests.single().headers[HttpHeaders.Authorization] shouldBe "Token abc"
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `a scrobble is queued for ListenBrainz with the play's start time and nothing is sent directly`() = runTest {
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)

        val queued = dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, 10).single()
        queued.track shouldBe "song-1"
        queued.startedAtEpochSec shouldBe 1_234
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        server.requests shouldBe emptyList()
    }

    @Test
    fun `signed out - nothing is sent or queued`() = runTest {
        store.signOut()

        scrobbler.nowPlaying(song)
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)

        server.requests shouldBe emptyList()
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `a rejected token from now playing signs the user out and keeps the queue`() = runTest {
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)
        server.enqueue("""{"code":401}""", HttpStatusCode.Unauthorized)

        scrobbler.nowPlaying(song)

        store.account.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }
}
