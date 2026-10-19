package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.shuttle.model.Song

data class ExcludedSongsViewState(
    val content: Content,
    val showIncludeAll: Boolean
) {
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        data class Songs(val rows: List<Row>) : Content
    }

    data class Row(val song: Song, val title: String, val subtitle: String)
}

fun excludedSongsViewState(uiState: ExcludedSongsUiState): ExcludedSongsViewState {
    val content = when {
        uiState.loading -> ExcludedSongsViewState.Content.Loading

        uiState.songs.isEmpty() -> ExcludedSongsViewState.Content.Empty

        else -> ExcludedSongsViewState.Content.Songs(
            uiState.songs.map { song ->
                ExcludedSongsViewState.Row(
                    song = song,
                    title = song.name.orEmpty(),
                    subtitle = listOfNotNull(song.friendlyArtistName, song.album).joinToString(" · ")
                )
            }
        )
    }
    return ExcludedSongsViewState(content = content, showIncludeAll = uiState.songs.isNotEmpty())
}
