package com.simplecityapps.shuttle.model

import android.os.Parcelable
import com.simplecityapps.shuttle.parcel.InstantParceler
import kotlinx.datetime.Instant
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

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
    @IgnoredOnParcel
    val friendlyArtistName: String?
        by lazy {
            if (artists.isNotEmpty()) {
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
}
