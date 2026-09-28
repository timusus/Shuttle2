package com.simplecityapps.localmediaprovider.local.data.room.entity

import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.AlbumIdentityTags
import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * The columns of a song that [AlbumIdentityRule] and [com.simplecityapps.shuttle.model.ArtistCredits] read: what a library's album identities are resolved from, without
 * reading every song whole. [SONG_IDENTITY_QUERY] selects them.
 */
data class SongIdentityData(
    val id: Long,
    val album: String?,
    val albumArtist: String?,
    val albumArtists: List<String>?,
    val artists: List<String>,
    val compilation: Boolean?,
    val mbAlbumId: String?,
    val serverAlbumId: String?,
    val mediaProvider: MediaProviderType,
    val path: String,
    val artistsTag: List<String>?,
    val mbArtistIds: List<String>?,
    val mbAlbumArtistIds: List<String>?,
    val serverArtistIds: List<String>?,
    val serverAlbumArtistIds: List<String>?
) {
    fun toTags(): AlbumIdentityTags = AlbumIdentityTags(
        id,
        album,
        albumArtist,
        albumArtists,
        artists,
        compilation,
        mbAlbumId,
        serverAlbumId,
        mediaProvider,
        path,
        artistsTag,
        mbArtistIds,
        mbAlbumArtistIds,
        serverArtistIds,
        serverAlbumArtistIds
    )
}

/** Every song's [SongIdentityData], in id order. */
const val SONG_IDENTITY_QUERY = "SELECT id, album, albumArtist, albumArtists, artists, compilation, mbAlbumId, serverAlbumId, mediaProvider, path, artistsTag, mbArtistIds, mbAlbumArtistIds, serverArtistIds, serverAlbumArtistIds FROM songs ORDER BY id"
