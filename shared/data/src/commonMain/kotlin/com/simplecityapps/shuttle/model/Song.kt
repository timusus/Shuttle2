package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.parcel.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.coroutines.EmptyCoroutineContext.get

@Parcelize
@TypeParceler<Instant?, InstantParceler>
@TypeParceler<Instant, InstantParceler>
@TypeParceler<LocalDate?, LocalDateParceler>
data class Song(
    val id: Long,
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val album: String?,
    val track: Int?,
    val disc: Int?,
    val duration: Int?,
    val date: LocalDate?,
    val genres: List<String>,
    val path: String,
    val size: Long?,
    val mimeType: String?,
    val dateModified: Instant?,
    val lastPlayed: Instant?,
    val lastCompleted: Instant?,
    val playCount: Int,
    val playbackPosition: Int,
    val blacklisted: Boolean,
    val externalId: String? = null,
    val mediaProvider: MediaProviderType,
    val replayGainTrack: Double? = null,
    val replayGainAlbum: Double? = null,
    val lyrics: String?,
    val grouping: String?,
    val bitRate: Int?,
    val sampleRate: Int?,
    val channelCount: Int?,
    val composer: String?,
    val dateAdded: Instant
) : Parcelable {

    val type: Type
        get() {
            return when {
                path.contains("audiobook", true) || path.endsWith("m4b", true) -> Type.Audiobook
                path.contains("podcast", true) -> Type.Podcast
                else -> Type.Audio
            }
        }

    enum class Type {
        Audio, Audiobook, Podcast
    }
}