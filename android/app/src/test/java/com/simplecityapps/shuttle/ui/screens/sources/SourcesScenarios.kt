package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Settings > Sources states the tests and screenshots render. */
object SourcesScenarios {
    /** "Now" for the status lines, so "Updated 2 hours ago" never moves. */
    val now: Instant = Instant.parse("2026-09-30T12:00:00Z")

    private fun servers(vararg connected: Pair<MediaProviderType, SourceStatus>) = ServerTypes.map { type -> connected.toMap()[type]?.let { ServerSource(type, connected = true, status = it, songs = 1_842, updated = now - 2.hours) } ?: ServerSource(type, connected = false) }

    /** This device on with an excluded and an extra folder, and Jellyfin connected; everything up to date. */
    val configured = SourcesUiState(
        thisDevice = true,
        folders = FolderLists(
            excludes = listOf(SourceFolder(uri = null, path = "/storage/emulated/0/Recordings", name = "Recordings")),
            extras = listOf(SourceFolder(uri = "content://tree/Audiobooks", path = "/storage/emulated/0/Audiobooks", name = "Audiobooks")),
        ),
        deviceSongs = 1_234,
        deviceUpdated = now - 2.hours,
        lastImport = now - 2.hours,
        servers = servers(MediaProviderType.Jellyfin to SourceStatus.Idle),
    )

    /** This device part-way through a scan while Jellyfin syncs. */
    val scanning = configured.copy(
        deviceStatus = SourceStatus.Importing(Progress(340, 1_234)),
        servers = servers(MediaProviderType.Jellyfin to SourceStatus.Importing(Progress(600, 1_842))),
    )

    /** Plex's last import couldn't reach it, next to a connected Jellyfin. */
    val serverUnreachable = configured.copy(
        servers = servers(MediaProviderType.Jellyfin to SourceStatus.Idle, MediaProviderType.Plex to SourceStatus.Failed("Couldn't reach the server")),
    )

    /** Jellyfin counts 3 songs it doesn't return, next to a Plex that lists everything. */
    val serverShortfall = configured.copy(
        servers = servers(MediaProviderType.Jellyfin to SourceStatus.Idle, MediaProviderType.Plex to SourceStatus.Idle).map { if (it.type == MediaProviderType.Jellyfin) it.copy(listingShortfall = 3) else it },
    )

    val noActions = SourcesActions(onThisDeviceChange = {}, onRescan = {}, onRetrySkippedFiles = {}, onOpenFolderRules = {}, onServerClick = {}, onAddServer = {}, onShowDialog = {})
}
