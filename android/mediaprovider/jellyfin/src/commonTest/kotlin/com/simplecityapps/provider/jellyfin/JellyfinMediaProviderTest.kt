package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.HttpRequestData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/** The Jellyfin sync engine against a fixture server serving JSON fixtures in the server's response shape (#347). */
class JellyfinMediaProviderTest {
    private val server = FixtureServer("jellyfin")

    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin")

    private val authenticationManager =
        JellyfinAuthenticationManager(
            userService = UserService(client),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel"),
            streamProfile = StreamProfile.Android
        )

    private val provider = JellyfinMediaProvider(TestServerStrings, authenticationManager, ItemsService(client))

    @AfterTest
    fun tearDown() {
        server.close()
    }

    // Songs, albums and artists

    @Test
    fun `songs are read from the user's audio items`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        val songs = sync()

        songs.map { it.name } shouldContainExactly listOf("Opening", "Duet", "B-Side")
        with(songs.first()) {
            externalId shouldBe "song-1"
            path shouldBe "jellyfin://item/song-1"
            mediaProvider shouldBe MediaProviderType.Jellyfin
            duration shouldBe 180_000
            genres shouldContainExactly listOf("Rock", "Indie")
            date shouldBe LocalDate(2020, 1, 1)
            lastModified shouldBe Instant.parse("2024-03-01T12:34:56Z")
        }
        songs.last().date shouldBe null
        songs.last().lastModified shouldBe null
    }

    @Test
    fun `the sync asks for every audio item with the session token`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        sync()

        val request = server.requestsTo(ITEMS).single()
        request.url.parameters["recursive"] shouldBe "true"
        request.url.parameters["fields"] shouldBe "Genres,DateCreated,ProviderIds,MediaStreams"
        request.url.parameters["startIndex"] shouldBe "0"
        request.url.parameters["limit"] shouldBe "500"
        request.headers["Authorization"]!! shouldContain "Token=\"token-1\""
    }

    @Test
    fun `songs carry the album - disc - track and artwork their albums are built from`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        val songs = sync()

        songs.map { Triple(it.album, it.disc, it.track) } shouldContainExactly
            listOf(Triple("First Album", 1, 1), Triple("First Album", 1, 2), Triple("Second Album", 2, 1))
        songs.map { it.artworkVersion } shouldContainExactly listOf("album-1-tag", "album-1-tag", null)
    }

    @Test
    fun `songs carry the album artist and track artists their artists are built from`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        val songs = sync()

        songs.map { it.albumArtist } shouldContainExactly listOf("Artist One", "Artist One", "Artist Two")
        songs.map { it.artists } shouldContainExactly
            listOf(listOf("Artist One"), listOf("Artist One", "Guest Singer"), listOf("Artist Two"))
    }

    @Test
    fun `an empty library syncs to no songs`() {
        signedIn()
        server.respond(ITEMS, "empty.json", query = mapOf("includeItemTypes" to "Audio"))

        sync().shouldBeEmpty()
    }

    // Paging

    @Test
    fun `a library larger than a page is fetched page by page`() {
        signedIn()
        server.respond(ITEMS, "songs_page_1.json", query = mapOf("includeItemTypes" to "Audio", "startIndex" to "0"))
        server.respond(ITEMS, "songs_page_2.json", query = mapOf("includeItemTypes" to "Audio", "startIndex" to "500"))

        val listing = syncEvent()
        val songs = listing.result

        songs.size shouldBe 502
        listing.complete shouldBe true
        songs.map { it.externalId }.let { ids -> ids.take(2) + ids.takeLast(2) } shouldContainExactly listOf("page-1-a", "page-1-b", "page-2-a", "page-2-b")
        server.requestsTo(ITEMS).map { it.url.parameters["startIndex"] to it.url.parameters["limit"] } shouldContainExactly
            listOf("0" to "500", "500" to "2")
    }

    @Test
    fun `a listing short of the server's total is incomplete - and the next sync's full listing complete`() {
        signedIn()
        // The server counts 5 items but returns 3, as Jellyfin's total counts rows it can't read
        server.respond(ITEMS, "songs_short.json", query = mapOf("includeItemTypes" to "Audio"))

        val short = syncEvent()

        short.result.size shouldBe 3
        short.complete shouldBe false
        short.missing shouldBe 2

        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        val complete = syncEvent()

        complete.result.size shouldBe 3
        complete.complete shouldBe true
    }

    @Test
    fun `paging reports progress through the library`() {
        signedIn()
        server.respond(ITEMS, "songs_page_1.json", query = mapOf("includeItemTypes" to "Audio", "startIndex" to "0"))
        server.respond(ITEMS, "songs_page_2.json", query = mapOf("includeItemTypes" to "Audio", "startIndex" to "500"))

        val progress = provider.findSongs(emptyList()).events().filterIsInstance<FlowEvent.Progress<*, MessageProgress>>()

        progress.mapNotNull { it.data.progress }.map { it.progress to it.total } shouldContainExactly listOf(500 to 502, 502 to 502)
    }

    // Playlists

    @Test
    fun `playlists hold the library songs their items refer to - in playlist order`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_1_items.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")
        val library = sync()

        val playlists = syncPlaylists(library)

        playlists.map { it.externalId } shouldContainExactly listOf("playlist-1", "playlist-2")
        with(playlists.first()) {
            name shouldBe "Road Trip"
            mediaProviderType shouldBe MediaProviderType.Jellyfin
            songs.map { it.externalId } shouldContainExactly listOf("song-3", "song-1")
        }
        playlists.last().name shouldBe TestServerStrings.unknownName
        playlists.last().songs.shouldBeEmpty()
    }

    @Test
    fun `playlist items are requested for the user`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_1_items.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        syncPlaylists(emptyList())

        server.requestsTo("/Playlists/playlist-1/Items").single().url.parameters["userId"] shouldBe "user-1"
    }

    @Test
    fun `a playlist read in full carries its version - its save date and item count`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_1_items.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        val listing = syncListing(emptyList())

        server.requestsTo(ITEMS).single().url.parameters.getAll("fields").orEmpty().single().split(',') shouldContainExactly listOf("DateLastSaved", "ChildCount")
        // playlist-2 has no save date, so has no version and is read every time
        listing.result.versions shouldBe mapOf("playlist-1" to "$SAVED/2")
        listing.result.unchanged.shouldBeEmpty()
    }

    @Test
    fun `a playlist at the version last read isn't read again - it's listed as unchanged`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        val listing = syncListing(emptyList(), knownVersions = mapOf("playlist-1" to "$SAVED/2", "playlist-2" to "anything"))

        server.requestsTo("/Playlists/playlist-1/Items").shouldBeEmpty()
        server.requestsTo("/Playlists/playlist-2/Items").size shouldBe 1
        listing.result.playlists.map { it.externalId } shouldContainExactly listOf("playlist-2")
        listing.result.unchanged shouldBe setOf("playlist-1")
        listing.result.versions shouldBe mapOf("playlist-1" to "$SAVED/2")
    }

    @Test
    fun `a playlist saved since it was last read is read again`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_1_items.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        val listing = syncListing(emptyList(), knownVersions = mapOf("playlist-1" to "2026-08-01T10:00:00.0000000Z/2"))

        server.requestsTo("/Playlists/playlist-1/Items").size shouldBe 1
        listing.result.unchanged.shouldBeEmpty()
        listing.result.versions shouldBe mapOf("playlist-1" to "$SAVED/2")
    }

    @Test
    fun `a playlist whose items fail to load is listed as unread - not taken as gone from the server`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", code = 500)
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        val listing = syncListing(emptyList())

        listing.result.playlists.map { it.externalId } shouldContainExactly listOf("playlist-2")
        listing.result.unread shouldBe setOf("playlist-1")
        listing.complete shouldBe true
    }

    @Test
    fun `every playlist is listed - whatever its media type`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "empty.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        syncPlaylists(emptyList())

        server.requestsTo(ITEMS).single().url.parameters["mediaTypes"] shouldBe null
    }

    @Test
    fun `a playlist whose items come to fewer than the server counts is listed as unread - with the songs it did return`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_items_short.json")
        server.respond("/Playlists/playlist-2/Items", "playlist_items_none.json")
        val library = sync()

        val listing = syncListing(library)

        listing.result.unread shouldBe setOf("playlist-1", "playlist-2")
        listing.result.playlists.map { it.externalId to it.songs.map { song -> song.externalId } } shouldContainExactly
            listOf("playlist-1" to listOf("song-3"), "playlist-2" to emptyList())
    }

    @Test
    fun `a listing short of the server's total says how many playlists it left out`() {
        signedIn()
        server.respond(ITEMS, "playlists_truncated.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "empty.json")

        syncListing(emptyList()).missing shouldBe 2
    }

    @Test
    fun `a playlist longer than a page keeps paging through the playlist's items - issue 524`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, "long_playlist.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-long/Items", "long_playlist_items_page_1.json", query = mapOf("startIndex" to "0"))
        server.respond("/Playlists/playlist-long/Items", "long_playlist_items_page_2.json", query = mapOf("startIndex" to "500"))
        val library = sync()
        server.clearRequests()

        val playlist = syncPlaylists(library).single()

        playlist.songs.map { it.externalId } shouldContainExactly listOf("song-1", "song-2")
        server.requestsTo("/Playlists/playlist-long/Items").map { it.url.parameters["startIndex"] to it.url.parameters["limit"] } shouldContainExactly
            listOf("0" to "500", "500" to "1")
        server.requestsTo(ITEMS).map { it.url.parameters["includeItemTypes"] } shouldContainExactly listOf("Playlist")
    }

    @Test
    fun `no playlists syncs to an empty list`() {
        signedIn()
        server.respond(ITEMS, "empty.json", query = mapOf("includeItemTypes" to "Playlist"))

        syncPlaylists(emptyList()).shouldBeEmpty()
    }

    // Authentication

    @Test
    fun `a stored login signs in first and syncs with the new session`() {
        credentialStore.address = server.address
        credentialStore.loginCredentials = LoginCredentials("shuttle-test", "secret")
        server.respond("/Users/AuthenticateByName", "authenticate.json", method = "POST")
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        sync().size shouldBe 3

        server.requestsTo(ITEMS).single().headers["Authorization"]!! shouldContain "Token=\"token-2\""
        authenticationManager.getAuthenticatedCredentials() shouldBe AuthenticatedCredentials("token-2", "user-1", canDownload = true)
    }

    @Test
    fun `a rejected sign-in fails the sync without querying the library`() {
        credentialStore.address = server.address
        credentialStore.loginCredentials = LoginCredentials("shuttle-test", "wrong")
        server.respond("/Users/AuthenticateByName", code = 401, method = "POST")

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe TestServerStrings.authenticationError
        server.requestsTo(ITEMS).shouldBeEmpty()
    }

    @Test
    fun `a session the server rejects signs out and fails the sync when there's no saved login`() {
        signedIn()
        server.respond("/Users/Me", code = 401)

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe TestServerStrings.authenticationError
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
        server.requestsTo(ITEMS).shouldBeEmpty()
    }

    @Test
    fun `a session the server rejects signs in again with the saved login and syncs`() {
        signedIn()
        credentialStore.loginCredentials = LoginCredentials("shuttle-test", "secret")
        server.respond("/Users/Me", code = 401)
        server.respond("/Users/AuthenticateByName", "authenticate.json", method = "POST")
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        sync().size shouldBe 3

        server.requestsTo(ITEMS).single().headers["Authorization"]!! shouldContain "Token=\"token-2\""
        authenticationManager.getAuthenticatedCredentials() shouldBe AuthenticatedCredentials("token-2", "user-1", canDownload = true)
    }

    @Test
    fun `a session the server rejects mid-sync signs in again with the saved login and carries on`() {
        signedIn()
        credentialStore.loginCredentials = LoginCredentials("shuttle-test", "secret")
        server.respond("/Users/AuthenticateByName", "authenticate.json", method = "POST")
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, code = 401, query = mapOf("includeItemTypes" to "Audio"), where = { request -> request.token == "token-1" })

        sync().size shouldBe 3

        server.requestsTo(ITEMS).map { request -> request.token } shouldBe listOf("token-1", "token-2")
        authenticationManager.getAuthenticatedCredentials()?.accessToken shouldBe "token-2"
    }

    @Test
    fun `a session the server rejects again after signing in again fails the sync`() {
        signedIn()
        credentialStore.loginCredentials = LoginCredentials("shuttle-test", "secret")
        server.respond("/Users/AuthenticateByName", "authenticate.json", method = "POST")
        server.respond(ITEMS, code = 401, query = mapOf("includeItemTypes" to "Audio"))

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "An error occurred. (401)"
        server.requestsTo(ITEMS).map { request -> request.token } shouldBe listOf("token-1", "token-2")
    }

    @Test
    fun `a session the server rejects mid-sync signs out and fails the sync with the server's error`() {
        signedIn()
        server.respond(ITEMS, code = 401, query = mapOf("includeItemTypes" to "Audio"))

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "An error occurred. (401)"
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    @Test
    fun `no sign-in fails the sync`() {
        credentialStore.address = server.address

        provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            TestServerStrings.authenticationError
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no server address fails the sync`() {
        provider.findSongs(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            TestServerStrings.addressMissing
        provider.findPlaylists(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            TestServerStrings.addressMissing
    }

    @Test
    fun `an incremental sync asks only for the items changed since it`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        provider.findSongsChangedSince(emptyList(), Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>()

        server.requestsTo(ITEMS).filter { it.url.parameters["filters"] == null }.single().url.parameters["minDateLastSaved"] shouldBe "2026-10-01T08:00:00Z"
    }

    @Test
    fun `an incremental sync brings the favourites changed on the server - which don't count as a change to the song`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        val stored = sync().map { song -> if (song.path == "jellyfin://item/song-1") song.copy(favouritedAt = Instant.parse("2026-09-01T00:00:00Z")) else song }
        server.respond(ITEMS, "empty.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, "favourites.json", query = mapOf("filters" to "IsFavorite"))

        val songs = provider.findSongsChangedSince(stored, Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result

        songs.associate { song -> song.path to (song.favouritedAt != null) } shouldBe mapOf("jellyfin://item/song-1" to false, "jellyfin://item/song-2" to true)
        server.requestsTo(ITEMS).single { it.url.parameters["filters"] == "IsFavorite" }.url.parameters["sortBy"] shouldBe "DateCreated,SortName"
    }

    @Test
    fun `an incremental sync short of the server's total says how many it's missing - alongside the favourites`() {
        signedIn()
        server.respond(ITEMS, "songs_short.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, "favourites.json", query = mapOf("filters" to "IsFavorite"))

        val listing = provider.findSongsChangedSince(emptyList(), Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>()

        listing.missing shouldBe 2
    }

    @Test
    fun `an incremental sync whose favourites fail to load leaves them as they are`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))
        val stored = sync()
        server.respond(ITEMS, "empty.json", query = mapOf("includeItemTypes" to "Audio"))
        server.respond(ITEMS, code = 500, query = mapOf("filters" to "IsFavorite"))

        provider.findSongsChangedSince(stored, Instant.parse("2026-10-01T08:00:00Z")).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result.shouldBeEmpty()
    }

    @Test
    fun `a full sync asks for every item whenever it changed`() {
        signedIn()
        server.respond(ITEMS, "songs.json", query = mapOf("includeItemTypes" to "Audio"))

        sync()

        server.requestsTo(ITEMS).single().url.parameters["minDateLastSaved"] shouldBe null
    }

    private fun signedIn() {
        credentialStore.address = server.address
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
        server.respond("/Users/Me", "me.json")
    }

    private fun sync(): List<Song> = syncEvent().result

    private fun syncEvent(): FlowEvent.Success<List<Song>> = provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>()

    private fun syncPlaylists(library: List<Song>): List<MediaImporter.PlaylistUpdateData> = syncListing(library).result.playlists

    private fun syncListing(
        library: List<Song>,
        knownVersions: Map<String, String> = emptyMap()
    ): FlowEvent.Success<MediaImporter.PlaylistListing> = provider.findPlaylists(library, knownVersions).events().last()
        .shouldBeInstanceOf<FlowEvent.Success<MediaImporter.PlaylistListing>>()

    private fun <T> Flow<T>.events(): List<T> = runBlocking { toList() }

    private val HttpRequestData.token: String?
        get() = headers["Authorization"]!!.substringAfter("Token=\"").substringBefore("\"")

    private companion object {
        const val SAVED = "2026-09-01T10:00:00.0000000Z"

        const val ITEMS = "/Users/user-1/Items"
    }
}
