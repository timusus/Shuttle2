package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import java.util.*

inline class ArtistGroupKey(val key: String?)

@Parcelize
data class AlbumGroupKey(
    val album: String?,
    val artistGroupKey: ArtistGroupKey
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
    val groupKey: AlbumGroupKey?
) : Parcelable

val Album.friendlyArtistName: String
    get() {
        return albumArtist
            ?: if (artists.size == 1) {
                artists.first()
            } else {
                artists.groupBy { it.toLowerCase(Locale.getDefault()).removeArticles() }
                    .map { map -> map.value.maxByOrNull { it.length } }
                    .joinToString(", ")
                    .ifEmpty { "Unknown" }
            }
    }