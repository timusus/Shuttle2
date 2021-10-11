package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class PlaylistData(
    val name: String,
    val mediaProvider: MediaProviderType,
    val externalId: String?,
    val songs: List<Song>
) : Parcelable