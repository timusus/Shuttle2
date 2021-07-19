package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.simplecityapps.mediaprovider.MediaProvider
import kotlinx.parcelize.Parcelize
import java.util.*

@Keep
@Parcelize
data class Song(
    val id: Long,
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val album: String?,
    val track: Int?,
    val disc: Int?,
    val duration: Int,
    val year: Int?,
    val genres: List<String>,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Date,
    val lastPlayed: Date?,
    val lastCompleted: Date?,
    val playCount: Int,
    val playbackPosition: Int,
    val blacklisted: Boolean,
    val externalId: String? = null,
    val mediaProvider: MediaProvider.Type,
    val replayGainTrack: Double? = null,
    val replayGainAlbum: Double? = null,
    val lyrics: String?,
    val grouping: String?,
    val bitRate: Int?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val channelCount: Int?
) : Parcelable {

    val type: Type
        get() {
            return when {
                path.contains("audiobook", true) || path.endsWith("m4b", true) -> Type.Audiobook
                path.contains("podcast", true) -> Type.Podcast
                else -> Type.Audio
            }
        }

    val albumArtistGroupKey: AlbumArtistGroupKey = AlbumArtistGroupKey(
        albumArtist?.lowercase(Locale.getDefault())?.removeArticles()
            ?: artists.joinToString(", ") { it.lowercase(Locale.getDefault()).removeArticles() }.ifEmpty { null }
    )

    val albumGroupKey = AlbumGroupKey(album?.lowercase(Locale.getDefault())?.removeArticles(), albumArtistGroupKey)

    enum class Type {
        Audio, Audiobook, Podcast
    }

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
}