package com.simplecityapps.shuttle.model

import android.os.Parcelable
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.parcelize.Parcelize

@Parcelize
data class SmartPlaylist(
    val nameResId: Int,
    val songQuery: SongQuery
) : Parcelable
