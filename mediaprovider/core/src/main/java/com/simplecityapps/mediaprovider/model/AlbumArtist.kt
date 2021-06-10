package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.simplecityapps.mediaprovider.MediaProvider
import kotlinx.parcelize.Parcelize
import java.util.*

@Keep
@Parcelize
data class AlbumArtist(
    val name: String?,
    val artists: List<String>,
    val albumCount: Int,
    val songCount: Int,
    val playCount: Int,
    val groupKey: ArtistGroupKey,
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

    val friendlyNameOrArtistName: String? = name ?: friendlyArtistName
}