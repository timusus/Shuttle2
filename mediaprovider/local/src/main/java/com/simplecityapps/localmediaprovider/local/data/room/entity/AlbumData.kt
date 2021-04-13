package com.simplecityapps.localmediaprovider.local.data.room.entity

data class AlbumData(
    val name: String?,
    val artist: List<String>,
    val albumArtist: String?,
    val songCount: Int,
    val year: Int?,
    val playCount: Int
)