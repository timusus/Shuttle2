package com.simplecityapps.shuttle.model

import android.os.Parcelable
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.parcelize.Parcelize

@Parcelize
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val duration: Int,
    val sortOrder: PlaylistSongSortOrder,
    val mediaProvider: MediaProviderType,
    val externalId: String?
) : Parcelable
