package com.simplecityapps.taglib

data class AudioFile(
    val name: String,
    val albumArtistName: String,
    val artistName: String,
    val albumName: String,
    val track: Int,
    val disc: Int,
    val duration: Int,
    val year: Int,
    val path: String,
    var size: Long,
    var lastModified: Long
)