package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.ARTIST
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.SCROBBLE
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.STAR
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.UNSTAR
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/** Plays reported through `scrobble`, favourites written as stars, and artwork urls that carry no credentials. */
class SubsonicReportingTest {
    private val subsonic = TestSubsonic()
    private val server = subsonic.server
    private val song = subsonicSong()

    @AfterTest
    fun tearDown() = subsonic.close()

    @Test
    fun `a start is reported as now playing and a finished play with when it was played`() {
        subsonic.signIn()
        server.respond(SCROBBLE, "ok.json")
        val reporter = SubsonicPlaybackReporter(subsonic.authenticationManager, subsonic.service)

        runBlocking {
            reporter.start(PlaybackSession(song, "play-1"), positionMs = 0) shouldBe true
            reporter.markPlayed(song, Instant.fromEpochMilliseconds(1_790_000_000_000)) shouldBe true
        }

        val (start, played) = server.requestsTo(SCROBBLE)
        start.parameter("id") shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
        start.parameter("submission") shouldBe "false"
        start.parameter("time").shouldBeNull()
        played.parameter("submission") shouldBe "true"
        played.parameter("time") shouldBe "1790000000000"
        reporter.handles(song) shouldBe true
    }

    @Test
    fun `a failed scrobble reports failure`() {
        subsonic.signIn()
        server.respond(SCROBBLE, "error_not_found.json")
        val reporter = SubsonicPlaybackReporter(subsonic.authenticationManager, subsonic.service)

        runBlocking { reporter.markPlayed(song, Instant.fromEpochMilliseconds(0)) } shouldBe false
    }

    @Test
    fun `a favourite is a star and clearing it unstars`() {
        subsonic.signIn()
        server.respond(STAR, "ok.json")
        server.respond(UNSTAR, "ok.json")
        val writer = SubsonicFavouriteWriter(subsonic.authenticationManager, subsonic.service)

        runBlocking {
            writer.setFavourite(song, favourite = true) shouldBe true
            writer.setFavourite(song, favourite = false) shouldBe true
        }

        server.requestsTo(STAR).single().parameter("id") shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
        server.requestsTo(UNSTAR).single().parameter("id") shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
    }

    @Test
    fun `album art is the song's cover art - with no credentials in the url`() {
        subsonic.signIn()
        val artwork = SubsonicRemoteArtworkProvider(subsonic.authenticationManager, subsonic.service)

        val url = Url(runBlocking { artwork.getAlbumArtworkUrl(song.copy(artworkVersion = "mf-5zTXFMk8oDiQF9kh1gqcJE")) }!!)

        url.encodedPath shouldBe "/rest/getCoverArt.view"
        url.parameters["id"] shouldBe "mf-5zTXFMk8oDiQF9kh1gqcJE"
        url.parameters["size"] shouldBe "1000"
        SUBSONIC_CREDENTIAL_PARAMETERS.forEach { url.parameters[it].shouldBeNull() }
    }

    @Test
    fun `where the image loader can't sign a request - the url comes signed`() {
        subsonic.signIn()
        val artwork = SubsonicRemoteArtworkProvider(subsonic.authenticationManager, subsonic.service)
        val signed = SignedSubsonicArtworkProvider(artwork, subsonic.authenticationManager, subsonic.service)

        val unsigned = runBlocking { signed.getAlbumArtworkUrl(song.copy(artworkVersion = "mf-5zTXFMk8oDiQF9kh1gqcJE")) }!!
        val url = Url(signed.requestUrl(unsigned)!!)

        Url(unsigned).parameters["u"] shouldBe null
        url.parameters["id"] shouldBe "mf-5zTXFMk8oDiQF9kh1gqcJE"
        url.parameters["u"] shouldBe TestSubsonic.USERNAME
        url.parameters["t"] shouldBe md5Hex(TestSubsonic.PASSWORD + url.parameters["s"])
        signed.requestUrl("https://elsewhere.example/art.png") shouldBe "https://elsewhere.example/art.png"
        signed.handles("subsonic") shouldBe true
    }

    @Test
    fun `album art falls back to the album's cover`() {
        subsonic.signIn()
        val artwork = SubsonicRemoteArtworkProvider(subsonic.authenticationManager, subsonic.service)

        val url = Url(runBlocking { artwork.getAlbumArtworkUrl(song.copy(artworkVersion = null)) }!!)

        url.parameters["id"] shouldBe "4nxF0cOO4HAm80KTNDfkof"
    }

    @Test
    fun `artist art is the named artist's cover art`() {
        subsonic.signIn()
        server.respond(ARTIST, "artist.json", query = mapOf("id" to "07LA8XP6U5De7mzuoBVPz4"))
        val artwork = SubsonicRemoteArtworkProvider(subsonic.authenticationManager, subsonic.service)

        val url = Url(runBlocking { artwork.getArtistArtworkUrl(song, "07LA8XP6U5De7mzuoBVPz4") }!!)

        url.parameters["id"] shouldBe "ar-07LA8XP6U5De7mzuoBVPz4_0"
        url.parameters["u"].shouldBeNull()
    }

    @Test
    fun `only API requests to the signed-in server are signed`() {
        isSubsonicArtworkRequest("http://music.local:4533/rest/getCoverArt.view?id=1", "http://music.local:4533") shouldBe true
        isSubsonicArtworkRequest("http://MUSIC.local:4533/navidrome/rest/getCoverArt.view?id=1", "http://music.local:4533/navidrome") shouldBe true
        isSubsonicArtworkRequest("http://music.local:4534/rest/getCoverArt.view?id=1", "http://music.local:4533") shouldBe false
        isSubsonicArtworkRequest("https://music.local:4533/rest/getCoverArt.view?id=1", "http://music.local:4533") shouldBe false
        isSubsonicArtworkRequest("http://elsewhere/rest/getCoverArt.view?id=1", "http://music.local:4533") shouldBe false
        isSubsonicArtworkRequest("http://music.local:4533/art.png", "http://music.local:4533") shouldBe false
        isSubsonicArtworkRequest("http://music.local:4533/rest/getCoverArt.view", null) shouldBe false
    }
}
