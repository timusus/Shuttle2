package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.sorting.albumLetterIndex
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val selectedAlbums: Set<Album> = emptySet(),
    val viewMode: ViewMode = ViewMode.Grid,
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
    val events: List<PendingEvent<AlbumListEvent>> = emptyList(),
    /** The albums' letter sections for a name sort ([albumLetterIndex]); null for any other sort. */
    val letterIndex: List<LetterSection>? = null,
) {
    /** [Scanning] while an import runs; the list still carries what's already imported, for a screen that keeps showing it. */
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedAlbums.isNotEmpty()
}

sealed interface AlbumListEvent {
    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleFailed(val reason: String?) : AlbumListEvent
}
