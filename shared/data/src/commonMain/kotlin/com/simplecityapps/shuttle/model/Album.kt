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
    val groupKey: AlbumKey?,
    val mediaProviders: List<MediaProviderType>
) : Parcelable, Identifiable {

    override val id: String
        get() = AlbumKey(name, albumArtist)
}


@Parcelize
data class AlbumKey(
    val key: String?,
    val albumArtistGroupKey: String?
) : Parcelable, Identifiable {

    override val id: String
        get() = "${key}_${albumArtistGroupKey}"
}
