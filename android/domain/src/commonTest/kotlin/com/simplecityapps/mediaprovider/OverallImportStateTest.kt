package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The library's import state comes from every source's, whichever reported last. */
class OverallImportStateTest {
    @Test
    fun `nothing imported yet is idle`() {
        overallImportState(emptyMap()) shouldBe SongImportState.Idle
    }

    @Test
    fun `a source still running keeps the library in progress after another finishes`() {
        overallImportState(
            mapOf(
                MediaProviderType.Jellyfin to SongImportState.ImportComplete(MediaProviderType.Jellyfin, error = null),
                MediaProviderType.Shuttle to SongImportState.ImportProgress(MediaProviderType.Shuttle, "Scanning", Progress(10, 100)),
            )
        ) shouldBe SongImportState.ImportProgress(MediaProviderType.Shuttle, "Scanning", Progress(10, 100))
    }

    @Test
    fun `the progress is that of every running source, unknown while any of them doesn't know its own`() {
        val local = SongImportState.ImportProgress(MediaProviderType.Shuttle, "Scanning", Progress(10, 100))
        overallImportState(
            mapOf(
                MediaProviderType.Plex to SongImportState.ImportProgress(MediaProviderType.Plex, "Fetching", Progress(50, 100)),
                MediaProviderType.Shuttle to local,
            )
        ) shouldBe SongImportState.ImportProgress(MediaProviderType.Shuttle, "Scanning", Progress(60, 200))

        overallImportState(
            mapOf(
                MediaProviderType.Plex to SongImportState.ImportProgress(MediaProviderType.Plex, "Connecting", progress = null),
                MediaProviderType.Shuttle to local,
            )
        ) shouldBe local.copy(progress = null)
    }

    @Test
    fun `once every source has finished a failure shows over a success`() {
        overallImportState(
            mapOf(
                MediaProviderType.Shuttle to SongImportState.ImportComplete(MediaProviderType.Shuttle, error = null),
                MediaProviderType.Plex to SongImportState.ImportComplete(MediaProviderType.Plex, "Server unreachable"),
            )
        ) shouldBe SongImportState.ImportComplete(MediaProviderType.Plex, "Server unreachable")
    }
}
