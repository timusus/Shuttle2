package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.ALBUM
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.ALBUM_LIST
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.PLAYLIST
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.PLAYLISTS
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.SEARCH
import com.simplecityapps.provider.subsonic.http.SongDto
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/** The Subsonic sync against fixtures captured from Navidrome: search3 paging, the album-by-album fallback, playlists. */
class SubsonicMediaProviderTest {
    private val subsonic = TestSubsonic()
    private val server = subsonic.server
    private val provider = SubsonicMediaProvider(TestServerStrings, subsonic.authenticationManager, subsonic.service)

    @AfterTest
    fun tearDown() = subsonic.close()

    // Songs

    @Test
    fun `songs are read a page at a time until a page comes back empty`() {
        subsonic.signIn()
        server.respond(SEARCH, "search3_page_1.json", query = mapOf("songOffset" to "0"))
        server.respond(SEARCH, "search3_page_2.json", query = mapOf("songOffset" to "3"))
        server.respond(SEARCH, "search3_empty.json", query = mapOf("songOffset" to "4"))

        val songs = syncSongs()

        songs.map { it.externalId } shouldContainExactly listOf("4S6nihsexXfLEd9rVm2LKr", "0ZVb9IcFZ0wbcBgwwir582", "5zTXFMk8oDiQF9kh1gqcJE", "0rkcRSZcVeI3YNESZf3Pui")
        server.requestsTo(SEARCH).first().let { request ->
            request.parameter("query") shouldBe ""
            request.parameter("songCount") shouldBe "500"
            request.parameter("artistCount") shouldBe "0"
            request.parameter("albumCount") shouldBe "0"
        }
    }

    @Test
    fun `a song maps its artists - album artists - genres - ids and audio details`() {
        subsonic.signIn()
        server.respond(SEARCH, "search3_page_1.json", query = mapOf("songOffset" to "0"))
        server.respond(SEARCH, "search3_empty.json", query = mapOf("songOffset" to "3"))

        val song = syncSongs().first { it.externalId == "4S6nihsexXfLEd9rVm2LKr" }

        song.name shouldBe "A Whole New World (Aladdin’s Theme)"
        song.artists shouldContainExactly listOf("Peabo Bryson", "Regina Belle")
        song.artistDisplay shouldBe "Peabo Bryson & Regina Belle"
        song.albumArtist shouldBe "Alan Menken"
        song.albumArtists shouldBe listOf("Alan Menken")
        song.genres shouldContainExactly listOf("Soundtrack")
        song.track shouldBe 21
        song.disc shouldBe 1
        song.date shouldBe LocalDate(1992, 1, 1)
        song.duration shouldBe 247_000
        song.path shouldBe "subsonic://song/4S6nihsexXfLEd9rVm2LKr"
        song.mediaProvider shouldBe MediaProviderType.Subsonic
        song.mimeType shouldBe "audio/flac"
        song.audioCodec shouldBe "flac"
        song.bitRate shouldBe 757
        song.bitDepth shouldBe 16
        song.sampleRate shouldBe 44100
        song.mbTrackId shouldBe "165f3d1e-168d-4642-9fa2-fac297b3d80d"
        song.serverAlbumId shouldBe "4nxF0cOO4HAm80KTNDfkof"
        song.serverArtistIds shouldBe listOf("4XVnsmsEkmk2E0NqAtB0Jx", "2fvetHX8g36Sb6XCnmrlSp")
        song.serverAlbumArtistIds shouldBe listOf("07LA8XP6U5De7mzuoBVPz4")
        song.artworkVersion shouldBe "mf-4S6nihsexXfLEd9rVm2LKr"
        song.dateAdded shouldBe Instant.parse("2026-10-05T01:03:50.358065972Z")
    }

    @Test
    fun `ReplayGain is kept when tagged and empty when the server sends none`() {
        subsonic.signIn()
        server.respond(SEARCH, "search3_page_1.json", query = mapOf("songOffset" to "0"))
        server.respond(SEARCH, "search3_empty.json", query = mapOf("songOffset" to "3"))

        val songs = syncSongs().associateBy { it.externalId }

        songs["0ZVb9IcFZ0wbcBgwwir582"]?.replayGainTrack shouldBe -4.01
        songs["0ZVb9IcFZ0wbcBgwwir582"]?.replayGainAlbum.shouldBeNull()
        songs["4S6nihsexXfLEd9rVm2LKr"]?.replayGainTrack.shouldBeNull()
    }

    @Test
    fun `a starred song is a favourite - with its play count`() {
        subsonic.signIn()
        server.respond(SEARCH, "search3_page_1.json", query = mapOf("songOffset" to "0"))
        server.respond(SEARCH, "search3_empty.json", query = mapOf("songOffset" to "3"))

        val songs = syncSongs().associateBy { it.externalId }

        songs["5zTXFMk8oDiQF9kh1gqcJE"]?.favouritedAt shouldBe Instant.parse("2026-09-30T08:15:00Z")
        songs["5zTXFMk8oDiQF9kh1gqcJE"]?.playCount shouldBe 7
        songs["4S6nihsexXfLEd9rVm2LKr"]?.favouritedAt.shouldBeNull()
        songs["4S6nihsexXfLEd9rVm2LKr"]?.playCount shouldBe 0
    }

    @Test
    fun `an m4a's codec is AAC or ALAC past any AAC bitrate`() {
        val aac = SongDto(id = "song", suffix = "m4a", bitRate = 256)
        val alac = SongDto(id = "song", suffix = "m4a", bitRate = 900)

        aac.audioCodec() shouldBe "aac"
        alac.audioCodec() shouldBe "alac"
    }

    @Test
    fun `a plain Subsonic server that finds nothing for an empty search is read album by album`() {
        subsonic.signIn(ping = "ping_subsonic.json")
        server.respond(SEARCH, "search3_empty.json")
        server.respond(ALBUM_LIST, "album_list.json", query = mapOf("offset" to "0"))
        server.respond(ALBUM_LIST, "album_list_empty.json", query = mapOf("offset" to "2"))
        server.respond(ALBUM, "album.json")

        val songs = syncSongs()

        server.requestsTo(ALBUM_LIST).first().parameter("type") shouldBe "alphabeticalByName"
        server.requestsTo(ALBUM).map { it.parameter("id") } shouldContainExactly listOf("3RcUiqVmOj5LztrSBWglLo", "7Fhz3XaE3wcpnr99q4VR3B")
        // Both albums answer with the same fixture; each song is kept once
        songs.map { it.externalId } shouldContainExactly listOf("4ybHCBhN2R5X5YAG1LyVKx", "032Sq9OndYALAaM6wobkf2")
    }

    @Test
    fun `an OpenSubsonic server that finds nothing has no songs`() {
        subsonic.signIn()
        server.respond(SEARCH, "search3_empty.json")

        syncSongs().shouldBeEmpty()
        server.requestsTo(ALBUM_LIST).shouldBeEmpty()
    }

    @Test
    fun `rejected credentials fail the sync and sign out`() {
        subsonic.signIn()
        server.respond(SEARCH, "error_wrong_credentials.json")

        val events = runBlocking { provider.findSongs(emptyList()).toList() }

        events.last().let { it as FlowEvent.Failure }
        subsonic.authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    // Playlists

    @Test
    fun `playlists hold the library's songs in order - skipping what the library doesn't have`() {
        subsonic.signIn()
        server.respond(PLAYLISTS, "playlists.json")
        server.respond(PLAYLIST, "playlist.json")
        val library = listOf(subsonicSong(externalId = "4S6nihsexXfLEd9rVm2LKr"), subsonicSong(externalId = "5zTXFMk8oDiQF9kh1gqcJE"))

        val playlists = syncPlaylists(library)

        server.requestsTo(PLAYLIST).map { it.parameter("id") } shouldContainExactly listOf("4OkvGqE1eXVi8lR27OxO8R", "6hxNgdRV8kzNLrIyAAvmx7")
        playlists.map { it.externalId } shouldContainExactly listOf("4OkvGqE1eXVi8lR27OxO8R", "6hxNgdRV8kzNLrIyAAvmx7")
        playlists.last().name shouldBe "Cassette Wrapped 2026"
        playlists.last().songs.map { it.externalId } shouldContainExactly listOf("4S6nihsexXfLEd9rVm2LKr", "5zTXFMk8oDiQF9kh1gqcJE")
    }

    @Test
    fun `a server with no playlists has none`() {
        subsonic.signIn()
        server.respond(PLAYLISTS, "playlists_empty.json")

        syncPlaylists(emptyList()).shouldBeEmpty()
    }

    private fun syncSongs(): List<Song> {
        val events = runBlocking { provider.findSongs(emptyList()).toList() }
        return (events.last() as FlowEvent.Success).result
    }

    private fun syncPlaylists(library: List<Song>): List<MediaImporter.PlaylistUpdateData> {
        val events = runBlocking { provider.findPlaylists(library).toList() }
        return (events.last() as FlowEvent.Success).result
    }
}
