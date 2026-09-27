package com.simplecityapps.provider.plex

import android.content.Context
import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FakeSharedPreferences
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.okHttpClient
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

/** The Plex sync engine against a MockWebServer serving JSON fixtures in the server's response shape (#575). */
@RunWith(RobolectricTestRunner::class)
class PlexMediaProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val server = FixtureServer("plex")

    private val retrofit =
        Retrofit.Builder()
            .baseUrl("${server.address}/")
            .client(server.okHttpClient())
            .addCallAdapterFactory(NetworkResultAdapterFactory(null))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
            .build()

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(FakeSharedPreferences()), "plex")

    private val authenticationManager =
        PlexAuthenticationManager(
            userService = retrofit.create(),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")
        )

    private val provider = PlexMediaProvider(context, authenticationManager, retrofit.create())

    @After
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
    fun `the sync finds the music section, then asks it for tracks with the session token`() {
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
            context.getString(R.string.media_provider_plex_music_library_missing)
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

        events.last().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe context.getString(R.string.media_provider_authentication_error)
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no server address fails the sync`() {
        provider.findSongs(emptyList()).events().single().shouldBeInstanceOf<FlowEvent.Failure>().message shouldBe
            context.getString(R.string.media_provider_address_missing)
    }

    private fun signedIn() {
        credentialStore.address = server.address
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
    }

    private fun sync(): List<Song> = provider.findSongs(emptyList()).events().last().shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result

    private fun <T> Flow<T>.events(): List<T> = runBlocking { toList() }

    private companion object {
        const val SECTIONS = "/library/sections"
        const val ITEMS = "/library/sections/2/all"
    }
}
