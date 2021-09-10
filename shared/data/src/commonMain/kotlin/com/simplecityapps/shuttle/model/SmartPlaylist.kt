package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.query.SongQuery

@Parcelize
data class SmartPlaylist(
    val nameResId: Int,
    val songQuery: SongQuery
) : Parcelable