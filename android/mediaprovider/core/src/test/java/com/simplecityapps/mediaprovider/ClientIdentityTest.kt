package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The per-install client id (#338) must survive across separate [SecurePreferenceManager] instances backed by the same store, so every server sees one stable device across app restarts. */
class ClientIdentityTest {
    private val sharedPreferences = FakeSharedPreferences()

    @Test
    fun `client id is generated once and stable across instances backed by the same store`() {
        val first = SecurePreferenceManager(sharedPreferences).getOrCreateClientId()
        val second = SecurePreferenceManager(sharedPreferences).getOrCreateClientId()

        second shouldBe first
    }

    @Test
    fun `client id is not blank`() {
        SecurePreferenceManager(sharedPreferences).getOrCreateClientId().isBlank() shouldBe false
    }
}
