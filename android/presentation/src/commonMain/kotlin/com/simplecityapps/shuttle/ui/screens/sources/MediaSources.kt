package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.StateFlow

/**
 * The media providers the library imports from, and the scan that imports them (S8 in
 * docs/architecture/ios-port/phase-4-platform-seams.md). Android's `DefaultMediaSources` persists the enabled set in
 * `PlaybackPreferenceManager.mediaProviderTypes` and mirrors it into `MediaImporter.mediaProviders`.
 */
interface MediaSources {
    val enabledTypes: StateFlow<List<MediaProviderType>>

    fun enable(type: MediaProviderType)

    /** Stops importing from [type] and removes its songs and playlists from the library and the queue. */
    fun disable(type: MediaProviderType)

    /** Whether an import has ever finished on this install. */
    val hasScanned: Boolean

    /** Imports from every enabled provider, outliving the screen that asked; a no-op while an import runs. */
    fun scan()

    /** Scans, turning the S2 scanner on first if no source on this device is: what a music permission grant starts. */
    fun scanThisDevice() {
        if (enabledTypes.value.none { it.isLocal }) enable(MediaProviderType.Shuttle)
        scan()
    }

    /**
     * Scans this device if the music permission is held but nothing has been scanned yet: granted over adb, or
     * restored with a backup. Asked once, at startup; the prompts that grant the permission scan for themselves.
     */
    fun scanIfNeverScanned(musicPermissionGranted: Boolean) {
        if (musicPermissionGranted && !hasScanned) scanThisDevice()
    }

    /** Whether the stored songs lack tags this build reads (`MediaImporter.songTagsOutdated`), until an import of every source succeeds. */
    val songTagsOutdated: Boolean

    /**
     * Imports again if the library was imported before this build's tags: every source is read in full and its songs
     * updated in place, keeping their ids, history and playlists. Asked once, at launch.
     */
    fun rescanIfSongTagsOutdated() {
        if (hasScanned && songTagsOutdated) scan()
    }
}

/** Songs on this device come from the S2 scanner, or the Android (MediaStore) provider for users who chose it before. */
val MediaProviderType.isLocal: Boolean get() = this == MediaProviderType.Shuttle || this == MediaProviderType.MediaStore
