package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class Genre(
    val name: String,
    val songs: List<Song>,
) : Parcelable {

    val mediaProviders: List<MediaProviderType> = songs.map { it.mediaProvider }.distinct()
    val duration = songs.sumOf { it.duration ?: 0 }
}