package com.simplecityapps.mediaprovider.server

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ServerCredentialStoreTest {
    private val keyValueStore = InMemoryKeyValueStore()

    private fun store(
        prefix: String,
        addressKey: String = "${prefix}_address"
    ) = ServerCredentialStore(SecurePreferenceManager(keyValueStore), prefix, addressKey)

    /** The keys Jellyfin, Emby and Plex each wrote before the store was shared, so existing sign-ins carry over. */
    private fun writeLegacyKeys(
        prefix: String,
        addressKey: String
    ) {
        keyValueStore.edit {
            putString("${prefix}_username", "tim")
            putString("${prefix}_pass", "secret")
            putString("${prefix}_access_token", "token")
            putString("${prefix}_user_id", "user")
            putBoolean("${prefix}_can_download", true)
            putString(addressKey, "https://server.example")
        }
    }

    @Test
    fun `reads the keys jellyfin and emby have always stored credentials under`() {
        for (prefix in listOf("jellyfin", "emby")) {
            writeLegacyKeys(prefix, "${prefix}_address")
            val store = store(prefix)

            store.loginCredentials shouldBe LoginCredentials("tim", "secret")
            store.authenticatedCredentials shouldBe AuthenticatedCredentials("token", "user", canDownload = true)
            store.address shouldBe "https://server.example"
        }
    }

    @Test
    fun `clearing forgets the address - the login and the session`() {
        writeLegacyKeys("plex", "plex_host")
        val store = store("plex", addressKey = "plex_host")

        store.clear()

        store.address.shouldBeNull()
        store.loginCredentials.shouldBeNull()
        store.authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `reads plex's address from plex_host`() {
        writeLegacyKeys("plex", "plex_host")
        val store = store("plex", addressKey = "plex_host")

        store.loginCredentials shouldBe LoginCredentials("tim", "secret")
        store.authenticatedCredentials shouldBe AuthenticatedCredentials("token", "user", canDownload = true)
        store.address shouldBe "https://server.example"
    }

    @Test
    fun `writes under the server's prefix`() {
        val store = store("jellyfin")

        store.loginCredentials = LoginCredentials("tim", "secret")
        store.authenticatedCredentials = AuthenticatedCredentials("token", "user", canDownload = true)
        store.address = "https://server.example"

        keyValueStore.values shouldBe mapOf(
            "jellyfin_username" to "tim",
            "jellyfin_pass" to "secret",
            "jellyfin_access_token" to "token",
            "jellyfin_user_id" to "user",
            "jellyfin_can_download" to true,
            "jellyfin_address" to "https://server.example"
        )
    }

    @Test
    fun `servers don't see each other's credentials`() {
        store("jellyfin").authenticatedCredentials = AuthenticatedCredentials("token", "user")

        store("emby").authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `never stores a two-factor code`() {
        val store = store("plex", addressKey = "plex_host")

        store.loginCredentials = LoginCredentials("tim", "secret", authCode = "123456")

        store.loginCredentials shouldBe LoginCredentials("tim", "secret", authCode = null)
    }

    @Test
    fun `null clears the credentials`() {
        val store = store("emby")
        store.loginCredentials = LoginCredentials("tim", "secret")
        store.authenticatedCredentials = AuthenticatedCredentials("token", "user", canDownload = true)

        store.loginCredentials = null
        store.authenticatedCredentials = null

        store.loginCredentials.shouldBeNull()
        store.authenticatedCredentials.shouldBeNull()
        keyValueStore.getBoolean("emby_can_download", true) shouldBe false
    }

    @Test
    fun `a session missing its user id reads as signed out`() {
        keyValueStore.edit { putString("jellyfin_access_token", "token") }

        store("jellyfin").authenticatedCredentials.shouldBeNull()
    }

    // A session the server rejects (#577)

    private val session = AuthenticatedCredentials("token", "user", canDownload = true)

    /** A signed-in store, and the [ServerCredentialStore.sessionExpired] signals it sends while the test runs. */
    private fun TestScope.signedIn(): Pair<ServerCredentialStore, List<Unit>> {
        val store = store("jellyfin")
        store.loginCredentials = LoginCredentials("tim", "secret")
        store.authenticatedCredentials = session
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.sessionExpired.toList(signals) }
        return store to signals
    }

    @Test
    fun `a 401 clears the session and signals it - keeping the saved login`() = runTest {
        val (store, signals) = signedIn()

        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Unauthorized)))

        store.authenticatedCredentials.shouldBeNull()
        store.loginCredentials shouldBe LoginCredentials("tim", "secret")
        signals.size shouldBe 1
    }

    private val unauthorized = NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Unauthorized))

    @Test
    fun `a 401 in a sync that signs in again signals nothing`() = runTest {
        val (store, signals) = signedIn()

        store.deferringExpiry {
            store.checkSession(session, unauthorized)
            signals.size shouldBe 0
            store.authenticatedCredentials = session.copy(accessToken = "token-2")
        }

        signals.size shouldBe 0
    }

    @Test
    fun `a 401 in a sync that can't sign in again signals once - when the sync ends`() = runTest {
        val (store, signals) = signedIn()

        store.deferringExpiry {
            store.checkSession(session, unauthorized)
            store.checkSession(session, unauthorized)
            signals.size shouldBe 0
        }

        signals.size shouldBe 1
    }

    @Test
    fun `a sync that sees no 401 signals nothing`() = runTest {
        val (store, signals) = signedIn()

        store.deferringExpiry { }

        signals.size shouldBe 0
    }

    @Test
    fun `other failures leave the session alone`() = runTest {
        val (store, signals) = signedIn()

        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Forbidden)))
        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.InternalServerError)))
        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.NotFound)))

        store.authenticatedCredentials shouldBe session
        signals.size shouldBe 0
    }

    @Test
    fun `requests rejected together sign out once`() = runTest {
        val (store, signals) = signedIn()

        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Unauthorized)))
        store.checkSession(session, NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Unauthorized)))

        signals.size shouldBe 1
    }

    @Test
    fun `a late 401 for a session since replaced leaves the new one`() = runTest {
        val (store, signals) = signedIn()
        val fresh = AuthenticatedCredentials("token-2", "user")
        store.authenticatedCredentials = fresh

        store.expireSession(session) shouldBe false

        store.authenticatedCredentials shouldBe fresh
        signals.size shouldBe 0
    }

    @Test
    fun `a refreshed download permission is still the same session`() = runTest {
        val (store, signals) = signedIn()
        store.authenticatedCredentials = session.copy(canDownload = false)

        store.expireSession(session) shouldBe true

        store.authenticatedCredentials.shouldBeNull()
        signals.size shouldBe 1
    }

    @Test
    fun `compare-and-set replaces the session it expects`() = runTest {
        val (store, _) = signedIn()
        val refreshed = session.copy(canDownload = false)

        store.compareAndSetAuthenticatedCredentials(expected = session, new = refreshed) shouldBe true

        store.authenticatedCredentials shouldBe refreshed
    }

    @Test
    fun `compare-and-set never brings back an expired session`() = runTest {
        val (store, _) = signedIn()
        store.expireSession(session)

        store.compareAndSetAuthenticatedCredentials(expected = session, new = session.copy(canDownload = false)) shouldBe false

        store.authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `compare-and-set never overwrites a newer sign-in`() = runTest {
        val (store, _) = signedIn()
        val fresh = AuthenticatedCredentials("token-2", "user")
        store.authenticatedCredentials = fresh

        store.compareAndSetAuthenticatedCredentials(expected = session, new = session.copy(canDownload = false)) shouldBe false

        store.authenticatedCredentials shouldBe fresh
    }

    @Test
    fun `sign-ins racing expiry never leave a half-written session`() = runTest {
        val store = store("jellyfin")
        val sessions = (1..2).map { AuthenticatedCredentials("token-$it", "user-$it") }
        store.authenticatedCredentials = sessions[0]

        // On real threads, so the lock is what keeps the session whole
        withContext(Dispatchers.Default) {
            val writers = sessions.map { credentials ->
                launch {
                    repeat(2_000) {
                        store.authenticatedCredentials = credentials
                        store.expireSession(credentials)
                    }
                }
            }
            repeat(2_000) {
                store.authenticatedCredentials?.let { read -> read.userId shouldBe "user-${read.accessToken.removePrefix("token-")}" }
            }
            writers.forEach { it.join() }
        }
    }
}
