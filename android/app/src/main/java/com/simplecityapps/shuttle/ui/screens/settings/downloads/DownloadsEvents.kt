package com.simplecityapps.shuttle.ui.screens.settings.downloads

/** What the downloads screen asks of its host. */
sealed interface DownloadsEvent {
    data object NavigateUp : DownloadsEvent

    data object RemoveAll : DownloadsEvent

    data class OpenAlbum(val album: DownloadedAlbum) : DownloadsEvent
}

class DownloadsCallbacks(
    val onNavigateUp: () -> Unit,
    val onRemoveAll: () -> Unit,
    val onOpenAlbum: (DownloadedAlbum) -> Unit
) {
    fun onEvent(event: DownloadsEvent) = when (event) {
        DownloadsEvent.NavigateUp -> onNavigateUp()
        DownloadsEvent.RemoveAll -> onRemoveAll()
        is DownloadsEvent.OpenAlbum -> onOpenAlbum(event.album)
    }
}
