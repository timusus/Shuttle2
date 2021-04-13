package com.simplecityapps.localmediaprovider.local.data.room.entity

data class AlbumArtistData(
    val name: String?,
    val artists: List<String>,
    val albumArtist: String?,
    val albumCount: Int?,
    val songCount: Int?,
    val playCount: Int
)