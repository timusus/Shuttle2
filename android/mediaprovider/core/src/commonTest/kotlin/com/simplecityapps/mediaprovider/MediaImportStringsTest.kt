package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MediaImportStringsTest {
    private val strings =
        object : MediaImportStrings {
            override fun connecting(provider: String) = "Connecting to $provider…"
            override val fetching = "Fetching your library…"
            override fun fetchingSongs(
                count: Int,
                total: Int
            ) = "Fetching $count of $total songs"
            override fun saving(count: Int) = "Saving $count songs…"
            override val importError = "Import failed"
        }

    private fun describe(progress: MessageProgress) = strings.describe(progress, provider = "Jellyfin")

    @Test
    fun `connecting names the server`() {
        describe(MessageProgress(ImportPhase.Connecting, progress = null)) shouldBe "Connecting to Jellyfin…"
    }

    @Test
    fun `fetching counts the songs once the provider knows how many there are`() {
        describe(MessageProgress(ImportPhase.Fetching, progress = null)) shouldBe "Fetching your library…"
        describe(MessageProgress(ImportPhase.Fetching, Progress(500, 5000))) shouldBe "Fetching 500 of 5000 songs"
    }

    @Test
    fun `saving counts the songs found`() {
        describe(MessageProgress(ImportPhase.Saving(songCount = 5000), progress = null)) shouldBe "Saving 5000 songs…"
    }

    @Test
    fun `a provider's own detail wins`() {
        describe(MessageProgress(ImportPhase.Fetching, Progress(3, 10), detail = "Artist • Song")) shouldBe "Artist • Song"
    }
}
