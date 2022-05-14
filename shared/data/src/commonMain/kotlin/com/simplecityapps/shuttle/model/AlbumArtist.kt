package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class AlbumArtist(
    val name: String?,
    val artists: List<Artist>,
    val albumCount: Int,
    val songCount: Int,
    val playCount: Int,
    val mediaProviders: List<MediaProviderType>
) : Parcelable