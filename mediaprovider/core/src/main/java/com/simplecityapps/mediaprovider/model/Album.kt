package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.simplecityapps.mediaprovider.MediaProvider
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class ArtistGroupKey(val key: String?) : Parcelable

@Parcelize
data class AlbumGroupKey(
    val key: String?,
    val artistGroupKey: ArtistGroupKey?
) : Parcelable

@Keep
@Parcelize
data class Album(
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val playCount: Int,
    val lastSongPlayed: Date?,
    val lastSongCompleted: Date?,
    val groupKey: AlbumGroupKey?,
    val mediaProviders: List<MediaProvider.Type>
) : Parcelable {

    val friendlyArtistName: String? = if (artists.isNotEmpty()) {
        if (artists.size == 1) {
            artists.first()
        } else {
            artists.groupBy { it.lowercase(Locale.getDefault()).removeArticles() }
                .map { map -> map.value.maxByOrNull { it.length } }
                .joinToString(", ")
                .ifEmpty { null }
        }
    } else {
        null
    }

    val friendlyAlbumArtistOrArtistName: String? = albumArtist ?: friendlyArtistName
}