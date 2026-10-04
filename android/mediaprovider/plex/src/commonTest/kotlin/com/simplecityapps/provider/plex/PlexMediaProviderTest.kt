package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/** The Plex sync engine against a fixture server serving JSON fixtures in the server's response shape (#575). */
class PlexMediaProviderTest {
    private val server = FixtureServer("plex")

    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex")

    private val authenticationManager =
        PlexAuthenticationManager(
            userService = UserService(client),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")
        )

    private val provider = PlexMediaProvider(TestServerStrings, TestPlexStrings, authenticationManager, ItemsService(client))

    @AfterTest
    fun tearDown() {
        server.close()
    }

    // Songs

    @Test
    fun `songs are read from the user's music library`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")

        val songs = sync()

        songs.map { it.name } shouldContainExactly listOf("Opening", "Duet", "B-Side")
        with(songs.first()) {
            externalId shouldBe "/library/parts/1/file.mp3"
            path shouldBe "plex:///library/metadata/1"
            mediaProvider shouldBe MediaProviderType.Plex
            album shouldBe "First Album"
            albumArtist shouldBe "Artist One"
        }
    }

    @Test
    fun `the sync finds the music section - then asks it for tracks with the session token`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")

        sync()

        server.requestsTo(SECTIONS).single().headers["X-Plex-Token"] shouldBe "token-1"
        val request = server.requestsTo(ITEMS).single()
        request.url.parameters["X-Plex-Container-Start"] shouldBe "0"
        request.url.parameters["X-Plex-Container-Size"] shouldBe "500"
        request.headers["X-Plex-Token"] shouldBe "token-1"
    }

    @Test
    fun `a music library with any name is synced`() {
        signedIn()
        server.respond(SECTIONS, "sections_renamed_music.json")
        server.respond(ITEMS, "songs.json")

        sync().map { it.name } shouldContainExactly listOf("Opening", "Duet", "B-Side")
    }

    @Test
    fun `every music library is synced - and the songs come as one result`() {
        signedIn()
        server.respond(SECTIONS, "sections_two_music.json")
        server.respond(ITEMS, "songs.json")
        server.respond("/library/sections/4/all", "songs_second_library.json")

        val events = provider.findSongs(emptyList()).events()

        events.filterIsInstance<FlowEvent.Success<*>>().size shouldBe 1
        events.last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result.map { it.name } shouldContainExactly
            listOf("Opening", "Duet", "B-Side", "Second Library Song")
        server.requestsTo("/library/sections/1/all").shouldBeEmpty()
    }

    @Test
    fun `a failing second library fails the sync`() {
        signedIn()
        server.respond(SECTIONS, "sections_two_music.json")
        server.respond(ITEMS, "songs.json")
        server.respond("/library/sections/4/all", code = 500)

        provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Failure>()
    }

    @Test
    fun `an empty library syncs to no songs`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "empty.json")

        sync().shouldBeEmpty()
    }

    @Test
    fun `a server with no music section fails the sync`() {
        signedIn()
        server.respond(SECTIONS, "sections_no_music.json")

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            TestPlexStrings.musicLibraryMissing
        server.requestsTo(ITEMS).shouldBeEmpty()
    }

    @Test
    fun `a failed sections request fails the sync with the server's error`() {
        signedIn()
        server.respond(SECTIONS, code = 500)

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "A server error occurred. (500)"
    }

    @Test
    fun `a failed items request fails the sync with the server's error`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, code = 500)

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "A server error occurred. (500)"
    }

    @Test
    fun `a session the server rejects signs out and fails the sync with the server's error`() {
        signedIn()
        server.respond(SECTIONS, code = 401)

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "An error occurred. (401)"
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
        server.requestsTo(ITEMS).shouldBeEmpty()
    }

    @Test
    fun `a session the server rejects mid-sync signs out`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, code = 401)

        provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Failure>()

        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    // Playlists

    @Test
    fun `playlists hold the library songs their items refer to - in playlist order`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")
        server.respond(PLAYLISTS, "playlists.json")
        server.respond("/playlists/11/items", "playlist_11_items.json")
        server.respond("/playlists/12/items", "empty.json")
        val library = sync()

        val playlists = syncPlaylists(library)

        playlists.map { it.externalId } shouldContainExactly listOf("11", "12")
        with(playlists.first()) {
            name shouldBe "Road Trip"
            mediaProviderType shouldBe MediaProviderType.Plex
            songs.map { it.name } shouldContainExactly listOf("B-Side", "Opening")
        }
        playlists.last().songs.shouldBeEmpty()
    }

    @Test
    fun `playlists are the server's audio playlists - asked for with the session token`() {
        signedIn()
        server.respond(PLAYLISTS, "playlists.json")
        server.respond("/playlists/11/items", "playlist_11_items.json")
        server.respond("/playlists/12/items", "empty.json")

        syncPlaylists(emptyList())

        val request = server.requestsTo(PLAYLISTS).single()
        request.url.parameters["playlistType"] shouldBe "audio"
        request.headers["X-Plex-Token"] shouldBe "token-1"
        server.requestsTo("/playlists/11/items").single().url.parameters["X-Plex-Container-Size"] shouldBe "500"
    }

    @Test
    fun `a playlist whose items fail to load is left out`() {
        signedIn()
        server.respond(PLAYLISTS, "playlists.json")
        server.respond("/playlists/11/items", code = 500)
        server.respond("/playlists/12/items", "empty.json")

        syncPlaylists(emptyList()).map { it.externalId } shouldContainExactly listOf("12")
    }

    @Test
    fun `a failed playlists request fails the sync with the server's error`() {
        signedIn()
        server.respond(PLAYLISTS, code = 500)

        provider.findPlaylists(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "A server error occurred. (500)"
    }

    // Paging

    @Test
    fun `a library larger than a page is fetched page by page`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs_page_1.json", query = mapOf("X-Plex-Container-Start" to "0"))
        server.respond(ITEMS, "songs_page_2.json", query = mapOf("X-Plex-Container-Start" to "500"))

        val songs = sync()

        songs.map { it.externalId } shouldContainExactly
            listOf("/library/parts/1/file.mp3", "/library/parts/2/file.mp3", "/library/parts/3/file.mp3", "/library/parts/4/file.mp3")
        server.requestsTo(ITEMS).map { it.url.parameters["X-Plex-Container-Start"] to it.url.parameters["X-Plex-Container-Size"] } shouldContainExactly
            listOf("0" to "500", "500" to "2")
    }

    @Test
    fun `paging reports progress through the library`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs_page_1.json", query = mapOf("X-Plex-Container-Start" to "0"))
        server.respond(ITEMS, "songs_page_2.json", query = mapOf("X-Plex-Container-Start" to "500"))

        val progress = provider.findSongs(emptyList()).events().filterIsInstance<FlowEvent.Progress<*, MessageProgress>>()

        progress.mapNotNull { it.data.progress }.map { it.progress to it.total } shouldContainExactly listOf(500 to 502, 502 to 502)
    }

    // Authentication

    @Test
    fun `a rejected sign-in fails the sync without querying the library`() {
        credentialStore.address = server.address

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe TestServerStrings.authenticationError
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no server address fails the sync`() {
        provider.findSongs(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            TestServerStrings.addressMissing
    }

    @Test
    fun `an incremental sync asks only for the items changed since it`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")

        provider.findSongsChangedSince(emptyList(), Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>()

        server.requestsTo(ITEMS).filter { it.url.parameters["userRating"] == null }.single().url.parameters["updatedAt>>"] shouldBe
            "${Instant.parse("2026-10-01T08:00:00Z").epochSeconds - 1}"
    }

    @Test
    fun `an incremental sync brings the tracks rated 5 stars or unrated since, which don't count as a change to the song`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")
        val stored = sync().map { song -> if (song.name == "Opening") song.copy(favouritedAt = Instant.parse("2026-09-01T00:00:00Z")) else song }
        server.respond(ITEMS, "empty.json")
        server.respond(ITEMS, "favourites.json", query = mapOf("userRating" to "10"))

        val songs = provider.findSongsChangedSince(stored, Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result

        // B-Side, rated 6, is in the reply as a server ignoring the filter would send it, and stays unfavourited
        songs.associate { song -> song.name to song.favouritedAt } shouldBe mapOf("Opening" to null, "Duet" to Instant.fromEpochSeconds(1_759_305_600))
    }

    @Test
    fun `an incremental sync whose favourites fail to load leaves them as they are`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")
        val stored = sync()
        server.respond(ITEMS, "empty.json")
        server.respond(ITEMS, code = 500, query = mapOf("userRating" to "10"))

        provider.findSongsChangedSince(stored, Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result.shouldBeEmpty()
    }

    @Test
    fun `a full sync asks for every item whenever it changed`() {
        signedIn()
        server.respond(SECTIONS, "sections.json")
        server.respond(ITEMS, "songs.json")

        sync()

        server.requestsTo(ITEMS).single().url.parameters["updatedAt>>"] shouldBe null
    }

    private fun signedIn() {
        credentialStore.address = server.address
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
    }

    private fun sync(): List<Song> = provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result

    private fun syncPlaylists(library: List<Song>): List<MediaImporter.PlaylistUpdateData> = provider.findPlaylists(library).events().last()
        .shouldBeInstanceOf<FlowEvent.Success<List<MediaImporter.PlaylistUpdateData>>>().result

    private fun <T> Flow<T>.events(): List<T> = runBlocking { toList() }

    private companion object {
        const val SECTIONS = "/library/sections"
        const val PLAYLISTS = "/playlists"
        const val ITEMS = "/library/sections/2/all"
    }
}
