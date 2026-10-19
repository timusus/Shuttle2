package com.simplecityapps.shuttle.ui.screens.settings.downloads

import io.kotest.matchers.shouldBe
import org.junit.Test

class DownloadsEventsTest {
    @Test
    fun `each event reaches its own handler`() {
        val album = downloadedAlbum(name = "Low Tide", songCount = 1)
        val reached = mutableListOf<String>()
        val opened = mutableListOf<DownloadedAlbum>()
        val callbacks = DownloadsCallbacks(
            onNavigateUp = { reached += "up" },
            onRemoveAll = { reached += "remove all" },
            onOpenAlbum = {
                reached += "open"
                opened += it
            }
        )

        listOf(DownloadsEvent.NavigateUp, DownloadsEvent.RemoveAll, DownloadsEvent.OpenAlbum(album))
            .forEach(callbacks::onEvent)

        reached shouldBe listOf("up", "remove all", "open")
        opened shouldBe listOf(album)
    }
}
