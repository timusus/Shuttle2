package com.simplecityapps.shuttle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Genre(
    val name: String,
    val songCount: Int,
    val duration: Int,
    val mediaProviders: List<MediaProviderType>
) : Parcelable
