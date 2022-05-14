package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder

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
