package com.simplecityapps.shuttle.ui.screens.settings.downloads

import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DownloadsViewStateTest {
    private fun album(name: String? = "Night Drive", artist: String? = "Artist") = DownloadedAlbum(
        key = name.orEmpty(),
        name = name,
        artist = artist,
        songCount = 10,
        bytes = 80_000_000L,
        cover = createSong()
    )

    @Test
    fun `loading shows the spinner and no remove all`() {
        val state = downloadsViewState(DownloadsUiState(loading = true))

        state.content shouldBe DownloadsViewState.Content.Loading
        state.showRemoveAll shouldBe false
    }

    @Test
    fun `nothing downloaded shows the empty state and no remove all`() {
        val state = downloadsViewState(DownloadsUiState(loading = false))

        state.content shouldBe DownloadsViewState.Content.Empty
        state.showRemoveAll shouldBe false
    }

    @Test
    fun `albums list in order with the storage used and remove all`() {
        val albums = listOf(album("Night Drive"), album("Low Tide"))

        val state = downloadsViewState(DownloadsUiState(albums = albums, storageBytes = 88_000_000L, loading = false))

        val content = state.content as DownloadsViewState.Content.Albums
        content.storageBytes shouldBe 88_000_000L
        content.rows.map { it.album } shouldBe albums
        content.rows.map { it.title } shouldBe listOf("Night Drive", "Low Tide")
        state.showRemoveAll shouldBe true
    }

    @Test
    fun `storage held without a complete album shows the storage and remove all instead of the empty state`() {
        val state = downloadsViewState(DownloadsUiState(storageBytes = 5_000_000L, loading = false))

        state.content shouldBe DownloadsViewState.Content.Albums(storageBytes = 5_000_000L, rows = emptyList())
        state.showRemoveAll shouldBe true
    }

    @Test
    fun `an unnamed album has no title and a missing artist is blank`() {
        val state = downloadsViewState(DownloadsUiState(albums = listOf(album(name = null, artist = null)), storageBytes = 1L, loading = false))

        val row = (state.content as DownloadsViewState.Content.Albums).rows.single()
        row.title shouldBe null
        row.artist shouldBe ""
    }
}
