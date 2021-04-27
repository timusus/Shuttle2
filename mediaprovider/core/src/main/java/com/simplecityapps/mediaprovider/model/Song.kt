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
    val mediaStoreId: Long? = null,
    val mediaProvider: MediaProvider.Type,
    val replayGainTrack: Double? = null,
    val replayGainAlbum: Double? = null,
    val lyrics: String?,
    val grouping: String?
) : Parcelable {

    val type: Type
        get() {
            return when {
                path.contains("audiobook", true) || path.endsWith("m4b", true) -> Type.Audiobook
                path.contains("podcast", true) -> Type.Podcast
                else -> Type.Audio
            }
        }

    val artistGroupKey: ArtistGroupKey
        get() = ArtistGroupKey(albumArtist?.toLowerCase(Locale.getDefault())?.removeArticles() ?: when (artists.size) {
            0 -> "Unknown"
            else -> artists.joinToString(", ") { it.toLowerCase(Locale.getDefault()).removeArticles() }.ifEmpty { "Unknown" }
        })

    val albumGroupKey: AlbumGroupKey
        get() = AlbumGroupKey(album?.toLowerCase(Locale.getDefault())?.removeArticles(), artistGroupKey)

    enum class Type {
        Audio, Audiobook, Podcast
    }
}

val Song.friendlyArtistName: String?
    get() {
        return if (artists.isEmpty()) {
            albumArtist
        } else {
            artists.groupBy { it.toLowerCase(Locale.getDefault()).removeArticles() }
                .map { map -> map.value.maxByOrNull { it.length } }
                .joinToString(", ")
                .ifEmpty { "Unknown" }
        }
    }