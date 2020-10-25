package com.simplecityapps.mediaprovider.model

data class AudioFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val title: String?,
    val albumArtist: String?,
    val artist: String?,
    val album: String?,
    val track: Int?,
    val trackTotal: Int?,
    val disc: Int?,
    val discTotal: Int?,
    val duration: Int?,
    val year: String?,
    var genres: List<String>
)