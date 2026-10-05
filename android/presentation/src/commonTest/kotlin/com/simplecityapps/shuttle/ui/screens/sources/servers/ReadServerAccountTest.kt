package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReadServerAccountTest {
    private fun account(login: SavedServerLogin) = ReadServerAccount(mapOf(MediaProviderType.Jellyfin to FakeServerAuthentication(login)))(MediaProviderType.Jellyfin)

    @Test
    fun `the host drops the scheme and path - and keeps the port`() {
        account(SavedServerLogin("https://music.example.com:8096/jellyfin/", "tim")) shouldBe "tim@music.example.com:8096"
    }

    @Test
    fun `an address without a user shows just the host`() {
        account(SavedServerLogin("http://plex.local:32400")) shouldBe "plex.local:32400"
    }

    @Test
    fun `nothing saved shows no account`() {
        account(SavedServerLogin()) shouldBe null
    }

    @Test
    fun `a type without a sign-in shows no account`() {
        ReadServerAccount(emptyMap())(MediaProviderType.Plex) shouldBe null
    }
}
