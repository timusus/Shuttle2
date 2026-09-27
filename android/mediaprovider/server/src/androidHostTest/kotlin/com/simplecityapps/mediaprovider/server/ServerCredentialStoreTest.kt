package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ServerCredentialStoreTest {
    private val sharedPreferences = FakeSharedPreferences()

    private fun store(
        prefix: String,
        addressKey: String = "${prefix}_address"
    ) = ServerCredentialStore(SecurePreferenceManager(sharedPreferences), prefix, addressKey)

    /** The keys Jellyfin, Emby and Plex each wrote before the store was shared, so existing sign-ins carry over. */
    private fun writeLegacyKeys(
        prefix: String,
        addressKey: String
    ) {
        sharedPreferences.edit()
            .putString("${prefix}_username", "tim")
            .putString("${prefix}_pass", "secret")
            .putString("${prefix}_access_token", "token")
            .putString("${prefix}_user_id", "user")
            .putBoolean("${prefix}_can_download", true)
            .putString(addressKey, "https://server.example")
            .apply()
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

        sharedPreferences.all shouldBe mapOf(
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
        sharedPreferences.getBoolean("emby_can_download", true) shouldBe false
    }

    @Test
    fun `a session missing its user id reads as signed out`() {
        sharedPreferences.edit().putString("jellyfin_access_token", "token").apply()

        store("jellyfin").authenticatedCredentials.shouldBeNull()
    }
}
