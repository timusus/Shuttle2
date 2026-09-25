package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaSourcesTest {
    private val mediaSources = FakeMediaSources()

    @Test
    fun `a grant held at startup before any scan enables the S2 scanner and scans`() {
        mediaSources.scanIfNeverScanned(musicPermissionGranted = true)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
    }

    @Test
    fun `a grant held at startup after a scan doesn't scan again`() {
        mediaSources.scan()

        mediaSources.scanIfNeverScanned(musicPermissionGranted = true)

        mediaSources.scans shouldBe 1
    }

    @Test
    fun `no grant at startup doesn't scan`() {
        mediaSources.scanIfNeverScanned(musicPermissionGranted = false)

        mediaSources.enabledTypes.value shouldBe emptyList()
        mediaSources.scans shouldBe 0
    }
}
