package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.scrobbling.createSong
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class LastFmScrobblerTest {
    private val dao = FakeScrobbleDao()
    private val server = FakeLastFmServer()
    private val sessionStore = FakeLastFmSessionStore(LastFmSession(key = "sk", username = "tim"))
    private val scrobbler = LastFmScrobbler(server.client(), sessionStore, ScrobbleQueue(dao, FakeScrobbleFlushScheduler()))
    private val song = createSong(id = 1, duration = 200_000)

    @Test
    fun `now playing sends the song with the session key and is never queued`() = runTest {
        scrobbler.nowPlaying(song)

        val form = server.form()
        form["method"] shouldBe "track.updateNowPlaying"
        form["sk"] shouldBe "sk"
        form["artist"] shouldBe "artist"
        form["track"] shouldBe "song-1"
        form["duration"] shouldBe "200"
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a scrobble is queued with the play's start time and nothing is sent directly`() = runTest {
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)

        val queued = dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, 10).single()
        queued.track shouldBe "song-1"
        queued.startedAtEpochSec shouldBe 1_234
        server.requests shouldBe emptyList()
    }

    @Test
    fun `signed out - nothing is sent or queued`() = runTest {
        sessionStore.signOut()

        scrobbler.nowPlaying(song)
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)

        server.requests shouldBe emptyList()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a song without an artist or track is neither sent nor queued`() = runTest {
        listOf(
            song.copy(name = " "),
            song.copy(name = null),
            song.copy(artists = emptyList(), albumArtist = null),
            song.copy(artists = listOf(""), albumArtist = null)
        ).forEach {
            scrobbler.nowPlaying(it)
            scrobbler.scrobble(it, startedAtEpochSec = 1_234)
        }

        server.requests shouldBe emptyList()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `an invalid session from now playing signs the user out and keeps the queue`() = runTest {
        scrobbler.scrobble(song, startedAtEpochSec = 1_234)
        server.enqueue("""{"error":9,"message":"Invalid session key"}""")

        scrobbler.nowPlaying(song)

        sessionStore.session.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }
}
