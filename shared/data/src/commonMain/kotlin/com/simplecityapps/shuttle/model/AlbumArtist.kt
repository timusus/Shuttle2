package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.Parcelable
import com.simplecityapps.shuttle.parcel.Parcelize

@Parcelize
data class AlbumArtist(
    val name: String?,
    val artists: List<String>,
    val albumCount: Int,
    val songCount: Int,
    val playCount: Int,
    val groupKey: AlbumArtistGroupKey,
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
