package com.simplecityapps.shuttle.persistence

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The keys [SecurePreferenceManager] saves under, pinned so a saved client id and credentials survive (#584). */
class SecurePreferenceManagerTest {
    private val store = InMemoryKeyValueStore()
    private val preferences = SecurePreferenceManager(store)

    @Test
    fun `the client id is saved under its key and clearing it removes the key`() {
        preferences.clientId.shouldBeNull()

        preferences.clientId = "client"
        store.values shouldBe mapOf("client_id" to "client")

        preferences.clientId = null
        store.values shouldBe emptyMap()
    }

    @Test
    fun `credentials are saved under the caller's keys`() {
        preferences.getBoolean("jellyfin_remember").shouldBe(false)

        preferences.putString("jellyfin_user", "user")
        preferences.putBoolean("jellyfin_remember", true)

        store.values shouldBe mapOf("jellyfin_user" to "user", "jellyfin_remember" to true)
        preferences.getString("jellyfin_user") shouldBe "user"
        preferences.getBoolean("jellyfin_remember") shouldBe true
    }
}
