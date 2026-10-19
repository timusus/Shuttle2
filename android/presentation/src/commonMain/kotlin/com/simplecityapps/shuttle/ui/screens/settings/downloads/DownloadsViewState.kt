package com.simplecityapps.shuttle.ui.screens.settings.downloads

data class DownloadsViewState(
    val content: Content,
    val showRemoveAll: Boolean
) {
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        /** Byte counts stay raw: the platform formats sizes. */
        data class Albums(val storageBytes: Long, val rows: List<Row>) : Content
    }

    /** A null [title] means the album is unnamed. */
    data class Row(val album: DownloadedAlbum, val title: String?, val artist: String)
}

/** Storage held without a complete album still lists the storage and Remove all rather than the empty state. */
fun downloadsViewState(uiState: DownloadsUiState): DownloadsViewState {
    val isEmpty = uiState.albums.isEmpty() && uiState.storageBytes == 0L
    val content = when {
        uiState.loading -> DownloadsViewState.Content.Loading

        isEmpty -> DownloadsViewState.Content.Empty

        else -> DownloadsViewState.Content.Albums(
            storageBytes = uiState.storageBytes,
            rows = uiState.albums.map { DownloadsViewState.Row(album = it, title = it.name, artist = it.artist.orEmpty()) }
        )
    }
    return DownloadsViewState(content = content, showRemoveAll = !isEmpty)
}
