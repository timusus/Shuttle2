package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class PlaylistSong(
    val id: Long,
    val sortOrder: Long,
    val song: Song
) : Parcelable