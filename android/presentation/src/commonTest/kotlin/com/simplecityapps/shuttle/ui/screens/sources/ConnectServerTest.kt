package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ConnectServerTest {
    @Test
    fun `connecting a server enables its provider and scans`() {
        val mediaSources = FakeMediaSources()
        val connectServer = ConnectServer(mediaSources)

        connectServer(MediaProviderType.Jellyfin)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Jellyfin)
        mediaSources.scans shouldBe 1
    }
}
