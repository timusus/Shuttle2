package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test

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
    fun `a library imported before this build's tags imports again once`() {
        mediaSources.scan()
        mediaSources.songTagsOutdated = true

        mediaSources.rescanIfSongTagsOutdated()
        mediaSources.rescanIfSongTagsOutdated()

        mediaSources.scans shouldBe 2
    }

    @Test
    fun `a library never imported isn't rescanned for its tags`() {
        mediaSources.songTagsOutdated = true

        mediaSources.rescanIfSongTagsOutdated()

        mediaSources.scans shouldBe 0
    }

    @Test
    fun `no grant at startup doesn't scan`() {
        mediaSources.scanIfNeverScanned(musicPermissionGranted = false)

        mediaSources.enabledTypes.value shouldBe emptyList()
        mediaSources.scans shouldBe 0
    }
}
