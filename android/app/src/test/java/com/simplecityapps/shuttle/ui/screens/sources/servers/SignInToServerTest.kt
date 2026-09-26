package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SignInToServerTest {
    private val plex = FakeServerAuthentication()
    private val jellyfin = FakeServerAuthentication()
    private val connected = mutableListOf<MediaProviderType>()
    private val signIn = SignInToServer(
        mapOf(MediaProviderType.Plex to plex, MediaProviderType.Jellyfin to jellyfin),
        ServerSignInAnalytics { connected += it },
    )
    private val login = ServerLogin("http://server:8096", "sam", "secret")

    @Test
    fun `a successful sign-in is recorded and remembers the login when asked`() = runTest {
        signIn(MediaProviderType.Jellyfin, login, rememberLogin = true) shouldBe SignInToServer.Result.Success

        jellyfin.authenticated shouldBe listOf(login)
        jellyfin.remembered shouldBe login
        plex.authenticated shouldBe emptyList()
        connected shouldBe listOf(MediaProviderType.Jellyfin)
    }

    @Test
    fun `a successful sign-in leaves the login unsaved when not asked to remember it`() = runTest {
        signIn(MediaProviderType.Plex, login, rememberLogin = false) shouldBe SignInToServer.Result.Success

        plex.remembered shouldBe null
        connected shouldBe listOf(MediaProviderType.Plex)
    }

    @Test
    fun `a failed sign-in reports why, and neither remembers the login nor is recorded`() = runTest {
        plex.failure = IllegalStateException("boom")

        signIn(MediaProviderType.Plex, login, rememberLogin = true) shouldBe SignInToServer.Result.Failure("An unknown error occurred.")

        plex.remembered shouldBe null
        connected shouldBe emptyList()
    }
}
