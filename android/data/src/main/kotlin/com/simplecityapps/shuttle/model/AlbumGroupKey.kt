package com.simplecityapps.shuttle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AlbumGroupKey(
    val key: String?,
    val albumArtistGroupKey: AlbumArtistGroupKey?
) : Parcelable
