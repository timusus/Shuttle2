package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The per-install client id (#338) must survive across separate [SecurePreferenceManager] instances backed by the same store, so every server sees one stable device across app restarts. */
class ClientIdentityTest {
    private val store = InMemoryKeyValueStore()

    @Test
    fun `client id is generated once and stable across instances backed by the same store`() {
        val first = SecurePreferenceManager(store).getOrCreateClientId()
        val second = SecurePreferenceManager(store).getOrCreateClientId()

        second shouldBe first
    }

    @Test
    fun `client id is not blank`() {
        SecurePreferenceManager(store).getOrCreateClientId().isBlank() shouldBe false
    }
}
