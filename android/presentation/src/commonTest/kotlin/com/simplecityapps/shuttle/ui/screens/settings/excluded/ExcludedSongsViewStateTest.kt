package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ExcludedSongsViewStateTest {
    private val songs = listOf(
        createSong(id = 1, name = "Alpha").copy(blacklisted = true),
        createSong(id = 2, name = "Beta").copy(blacklisted = true)
    )

    @Test
    fun `loading shows the spinner and no include all`() {
        val state = excludedSongsViewState(ExcludedSongsUiState(songs = emptyList(), loading = true))

        state.content shouldBe ExcludedSongsViewState.Content.Loading
        state.showIncludeAll shouldBe false
    }

    @Test
    fun `nothing excluded shows the empty state and no include all`() {
        val state = excludedSongsViewState(ExcludedSongsUiState(songs = emptyList(), loading = false))

        state.content shouldBe ExcludedSongsViewState.Content.Empty
        state.showIncludeAll shouldBe false
    }

    @Test
    fun `excluded songs become rows in order with include all`() {
        val state = excludedSongsViewState(ExcludedSongsUiState(songs = songs, loading = false))

        val rows = (state.content as ExcludedSongsViewState.Content.Songs).rows
        rows.map { it.title } shouldBe listOf("Alpha", "Beta")
        rows.map { it.song } shouldBe songs
        state.showIncludeAll shouldBe true
    }

    @Test
    fun `row subtitle joins artist and album`() {
        val song = createSong(name = "Alpha", artists = listOf("Artist"), album = "Album").copy(blacklisted = true)

        val state = excludedSongsViewState(ExcludedSongsUiState(songs = listOf(song), loading = false))

        (state.content as ExcludedSongsViewState.Content.Songs).rows.single().subtitle shouldBe "Artist · Album"
    }
}
