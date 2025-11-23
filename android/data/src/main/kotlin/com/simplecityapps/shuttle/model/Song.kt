package com.simplecityapps.shuttle.model

import android.os.Parcelable
import com.simplecityapps.shuttle.parcel.InstantParceler
import com.simplecityapps.shuttle.parcel.LocalDateParceler
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@TypeParceler<Instant?, InstantParceler>
@TypeParceler<LocalDate?, LocalDateParceler>
data class Song(
    val id: Long,
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val album: String?,
    val track: Int?,
    val disc: Int?,
    val duration: Int,
    val date: LocalDate?,
    val genres: List<String>,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Instant?,
    val lastPlayed: Instant?,
    val lastCompleted: Instant?,
    val playCount: Int,
    val playbackPosition: Int,
    val rating: Int = 0, // 0-5 star rating (0 = unrated)
    val blacklisted: Boolean,
    val externalId: String? = null,
    val mediaProvider: MediaProviderType,
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

    @IgnoredOnParcel
    val albumArtistGroupKey: AlbumArtistGroupKey by lazy {
        AlbumArtistGroupKey(
            albumArtist?.lowercase()?.removeArticles()
                ?: artists.joinToString(", ") { it.lowercase().removeArticles() }.ifEmpty { null }
        )
    }

    @IgnoredOnParcel
    val albumGroupKey by lazy { AlbumGroupKey(album?.lowercase()?.removeArticles(), albumArtistGroupKey) }

    enum class Type {
        Audio,
        Audiobook,
        Podcast
    }

    @IgnoredOnParcel
    val friendlyArtistName: String? by lazy {
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
