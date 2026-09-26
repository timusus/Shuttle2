package com.simplecityapps.provider.jellyfin

import android.content.Context
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

/** The Jellyfin sync engine against a MockWebServer serving JSON fixtures in the server's response shape (#347). */
@RunWith(RobolectricTestRunner::class)
class JellyfinMediaProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val server = JellyfinServer()

    private val retrofit =
        Retrofit.Builder()
            .baseUrl("${server.address}/")
            .addCallAdapterFactory(NetworkResultAdapterFactory(null))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
            .build()

    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences()))

    private val authenticationManager =
        JellyfinAuthenticationManager(
            userService = retrofit.create(),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")
        )

    private val provider = JellyfinMediaProvider(context, authenticationManager, retrofit.create())

    @After
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
        request.url.queryParameter("recursive") shouldBe "true"
        request.url.queryParameter("fields") shouldBe "Genres,DateCreated"
        request.url.queryParameter("startIndex") shouldBe "0"
        request.url.queryParameter("limit") shouldBe "500"
        request.headers["Authorization"]!! shouldContain "Token=\"token-1\""
    }

    @Test
    fun `songs carry the album, disc, track and artwork their albums are built from`() {
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

        val songs = sync()

        songs.map { it.externalId } shouldContainExactly listOf("page-1-a", "page-1-b", "page-2-a", "page-2-b")
        server.requestsTo(ITEMS).map { it.url.queryParameter("startIndex") to it.url.queryParameter("limit") } shouldContainExactly
            listOf("0" to "500", "500" to "2")
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
    fun `playlists hold the library songs their items refer to, in playlist order`() {
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
        playlists.last().name shouldBe context.getString(com.simplecityapps.core.R.string.unknown)
        playlists.last().songs.shouldBeEmpty()
    }

    @Test
    fun `playlist items are requested for the user`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", "playlist_1_items.json")
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        syncPlaylists(emptyList())

        server.requestsTo("/Playlists/playlist-1/Items").single().url.queryParameter("userId") shouldBe "user-1"
    }

    @Test
    fun `a playlist whose items fail to load is left out`() {
        signedIn()
        server.respond(ITEMS, "playlists.json", query = mapOf("includeItemTypes" to "Playlist"))
        server.respond("/Playlists/playlist-1/Items", code = 500)
        server.respond("/Playlists/playlist-2/Items", "empty.json")

        syncPlaylists(emptyList()).map { it.externalId } shouldContainExactly listOf("playlist-2")
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

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe context.getString(R.string.media_provider_authentication_error)
        server.requestsTo(ITEMS).shouldBeEmpty()
    }

    @Test
    fun `a session the server rejects fails the sync with the server's error`() {
        signedIn()
        server.respond("/Users/Me", code = 401)
        server.respond(ITEMS, code = 401, query = mapOf("includeItemTypes" to "Audio"))

        val events = provider.findSongs(emptyList()).events()

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe "An error occurred. (401)"
    }

    @Test
    fun `no sign-in fails the sync`() {
        credentialStore.address = server.address

        provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            context.getString(R.string.media_provider_authentication_error)
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no server address fails the sync`() {
        provider.findSongs(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            context.getString(R.string.media_provider_address_missing)
        provider.findPlaylists(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            context.getString(R.string.media_provider_address_missing)
    }

    private fun signedIn() {
        credentialStore.address = server.address
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
        server.respond("/Users/Me", "me.json")
    }

    private fun sync(): List<Song> = provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result

    private fun syncPlaylists(library: List<Song>): List<MediaImporter.PlaylistUpdateData> = provider.findPlaylists(library).events().last()
        .shouldBeInstanceOf<FlowEvent.Success<List<MediaImporter.PlaylistUpdateData>>>().result

    private fun <T> Flow<T>.events(): List<T> = runBlocking { toList() }

    private companion object {
        const val ITEMS = "/Users/user-1/Items"
    }
}
