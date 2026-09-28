package com.simplecityapps.mediaprovider.model

data class AudioFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val title: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val album: String?,
    val track: Int?,
    val trackTotal: Int?,
    val disc: Int?,
    val discTotal: Int?,
    val duration: Int?,
    val year: String?,
    val genres: List<String>,
    val replayGainTrack: Double?,
    val replayGainAlbum: Double?,
    val lyrics: String?,
    val grouping: String?,
    val bitRate: Int?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val channelCount: Int?,
    // The raw artist and album tags and MusicBrainz ids (#637), as [com.simplecityapps.shuttle.model.Song] stores them.
    // Empty or null where the file doesn't have the tag.
    val albumArtists: List<String> = emptyList(),
    val artistsTag: List<String> = emptyList(),
    val artistDisplay: String? = null,
    val compilation: Boolean? = null,
    val mbTrackId: String? = null,
    val mbAlbumId: String? = null,
    val mbReleaseGroupId: String? = null,
    val mbArtistIds: List<String> = emptyList(),
    val mbAlbumArtistIds: List<String> = emptyList()
)
