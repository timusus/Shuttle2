package com.simplecityapps.localmediaprovider.model

data class AudioFile(
    val name: String,
    val albumArtistName: String,
    val artistName: String,
    val albumName: String,
    val track: Int,
    val trackTotal: Int,
    val disc: Int,
    val discTotal: Int,
    val duration: Long,
    val year: Int,
    val path: String,
    var size: Long,
    var lastModified: Long
)