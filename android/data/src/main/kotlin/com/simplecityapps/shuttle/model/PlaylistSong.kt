package com.simplecityapps.shuttle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlaylistSong(
    val id: Long,
    val sortOrder: Long,
    val song: Song
) : Parcelable
