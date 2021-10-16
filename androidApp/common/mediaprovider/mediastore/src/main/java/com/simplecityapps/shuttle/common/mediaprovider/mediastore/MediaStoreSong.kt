package com.simplecityapps.shuttle.common.mediaprovider.mediastore

data class MediaStoreSong(
    val playOrder: Long,
    val title: String?,
    val album: String?,
    val artist: String?,
    val albumArtist: String?,
    val duration: Int,
    val year: Int?,
    val track: Int,
    val mimeType: String,
    val path: String
)