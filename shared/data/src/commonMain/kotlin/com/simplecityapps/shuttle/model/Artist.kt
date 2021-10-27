package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class Artist(
    val name: String?,
    val albumArtists: List<AlbumArtist>,
    val albumCount: Int,
    val songCount: Int,
    val playCount: Int,
    val mediaProvider: MediaProviderType
) : Parcelable {

    val id: String
        get() {
            return "${mediaProvider}_${name}"
        }
}