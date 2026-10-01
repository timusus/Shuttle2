package com.simplecityapps.shuttle.ui.screens.settings.backup

import kotlinx.serialization.Serializable

/**
 * Versioned library backup: per-song stats plus playlists, nothing else.
 *
 * Identity is (provider, path) first, (provider, externalId) for remote items, and a tag
 * fingerprint as fallback. Room ids, MediaStore ids-as-primary, credentials, SAF grant URIs and
 * absolute volume paths are deliberately NOT part of the format: none of them survive a reinstall.
 * Times are epoch millis (stable across kotlinx-datetime/-time versions).
 */
@Serializable
data class LibraryBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String? = null,
    /** Epoch millis the backup was written. */
    val exportedAt: Long,
    val songs: List<BackedUpSong>,
    val playlists: List<BackedUpPlaylist>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class SongIdentity(
    /** [com.simplecityapps.shuttle.model.MediaProviderType.name]. */
    val provider: String,
    val path: String,
    val externalId: String? = null,
    val title: String? = null,
    val album: String? = null,
    /** albumArtist ?: artists.joinToString, as displayed. */
    val artist: String? = null,
    val duration: Int = 0,
    val size: Long = 0
)

@Serializable
data class BackedUpSong(
    val identity: SongIdentity,
    val playCount: Int = 0,
    /** Epoch millis, null when never played. */
    val lastPlayed: Long? = null,
    val lastCompleted: Long? = null,
    val playbackPosition: Int = 0,
    val excluded: Boolean = false,
    /** Epoch millis, null when not a favourite. */
    val favouritedAt: Long? = null,
    /** Epoch millis, null when unknown. */
    val dateAdded: Long? = null
)

@Serializable
data class BackedUpPlaylist(
    val name: String,
    /** [com.simplecityapps.shuttle.model.MediaProviderType.name]. */
    val provider: String,
    val externalId: String? = null,
    /** [com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder.name]. */
    val sortOrder: String = "Position",
    val sortDescending: Boolean = false,
    val members: List<SongIdentity>
)
