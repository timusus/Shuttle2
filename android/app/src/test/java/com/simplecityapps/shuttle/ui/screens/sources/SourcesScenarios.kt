package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.shuttle.model.MediaProviderType

/** Settings > Sources states the screenshot tests render. */
object SourcesScenarios {
    /** This device on with an excluded and an extra folder, and Jellyfin connected. */
    val configured = SourcesUiState(
        thisDevice = true,
        folders = FolderLists(
            excludes = listOf(SourceFolder(uri = null, path = "/storage/emulated/0/Recordings", name = "Recordings")),
            extras = listOf(SourceFolder(uri = "content://tree/Audiobooks", path = "/storage/emulated/0/Audiobooks", name = "Audiobooks")),
        ),
        servers = ServerTypes.map { ServerSource(it, connected = it == MediaProviderType.Jellyfin) },
    )

    val noActions = SourcesActions(onThisDeviceChange = {}, onAddFolder = {}, onRescan = {}, onServerClick = {}, onShowDialog = {})
}
