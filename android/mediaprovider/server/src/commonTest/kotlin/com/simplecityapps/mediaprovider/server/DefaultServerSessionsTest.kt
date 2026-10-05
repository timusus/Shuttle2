package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultServerSessionsTest {
    private val session = AuthenticatedCredentials("token", "user", canDownload = true)
    private val jellyfin = store("jellyfin")
    private val emby = store("emby")
    private val plex = store("plex")
    private val subsonic = store("subsonic")
    private val sessions = DefaultServerSessions(jellyfin, emby, plex, subsonic)

    private fun store(prefix: String) = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), prefix)

    @Test
    fun `each server's expired session maps to its own provider type`() = runTest {
        val expired = mutableListOf<MediaProviderType>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sessions.expired.toList(expired) }

        for (store in listOf(jellyfin, emby, plex, subsonic)) {
            store.authenticatedCredentials = session
            store.expireSession(session)
        }

        expired shouldBe listOf(MediaProviderType.Jellyfin, MediaProviderType.Emby, MediaProviderType.Plex, MediaProviderType.Subsonic)
    }
}
