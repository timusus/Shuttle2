package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class PlaylistSong(
    val id: Long,
    val sortOrder: Long,
    val song: Song
) : Parcelable