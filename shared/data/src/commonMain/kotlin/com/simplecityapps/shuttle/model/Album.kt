package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.InstantParceler
import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize
import com.simplecityapps.shuttle.parcel.TypeParceler
import kotlinx.datetime.Instant

@Parcelize
data class AlbumGroupKey(
    val key: String?,
    val albumArtistGroupKey: AlbumArtistGroupKey?
) : Parcelable

@Parcelize
@TypeParceler<Instant?, InstantParceler>
data class Album(
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val playCount: Int,
    val lastSongPlayed: Instant?,
    val lastSongCompleted: Instant?,
    val groupKey: AlbumGroupKey?,
    val mediaProviders: List<MediaProviderType>
) : Parcelable {

    val friendlyArtistName: String? = if (artists.isNotEmpty()) {
        if (artists.size == 1) {
            artists.first()
        } else {
            artists.groupBy { it.lowercase().removeArticles() }
                .map { map -> map.value.maxByOrNull { it.length } }
                .joinToString(", ")
                .ifEmpty { null }
        }
    } else {
        null
    }
}