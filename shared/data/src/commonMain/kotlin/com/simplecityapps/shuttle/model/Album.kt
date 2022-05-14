package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.InstantParceler
import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.parcel.TypeParceler
import kotlinx.datetime.Instant

@Parcelize
@TypeParceler<Instant?, InstantParceler>
data class Album(
    val name: String?,
    val albumArtist: AlbumArtist?,
    val artists: List<Artist>,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val playCount: Int,
    val lastSongPlayed: Instant?,
    val lastSongCompleted: Instant?,
    val mediaProviders: List<MediaProviderType>
) : Parcelable