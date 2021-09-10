package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class Genre(
    val name: String,
    val songCount: Int,
    val duration: Int,
    val mediaProviders: List<MediaProviderType>
) : Parcelable