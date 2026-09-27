package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeQuickConnectAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CheckQuickConnectAvailableTest {
    private val jellyfin = FakeQuickConnectAuthentication()
    private val check = CheckQuickConnectAvailable(mapOf(MediaProviderType.Jellyfin to jellyfin))

    @Test
    fun `reports what the server says`() = runTest {
        jellyfin.enabled = true
        check(MediaProviderType.Jellyfin, "http://server") shouldBe true

        jellyfin.enabled = false
        check(MediaProviderType.Jellyfin, "http://server") shouldBe false
    }

    @Test
    fun `a type with no Quick Connect binding is false, without asking the server`() = runTest {
        check(MediaProviderType.Plex, "http://server") shouldBe false
    }

    @Test
    fun `a check that throws is treated as unavailable`() = runTest {
        jellyfin.enabledCheckThrows = IllegalArgumentException("malformed address")

        check(MediaProviderType.Jellyfin, "not a url") shouldBe false
    }
}
