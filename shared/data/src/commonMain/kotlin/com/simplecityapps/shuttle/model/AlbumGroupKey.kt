package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class AlbumGroupKey(
    val key: String?,
    val albumArtistGroupKey: AlbumArtistGroupKey?
) : Parcelable
