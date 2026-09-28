package com.simplecityapps.shuttle.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
    val channelCount: Int?,
    // The source audio codec (e.g. "alac", "flac"), when the provider's metadata carries one; null when it
    // doesn't (Jellyfin, Emby) or is unknown. Distinct from mimeType/container: a codec the player can't
    // decode can still sit inside an otherwise-playable container, e.g. ALAC in an .m4a.
    val audioCodec: String? = null,
    // Opaque token from the song's provider that changes whenever its artwork does; null when the
    // provider has none. Artwork cache keys include it, so art refreshes on the next sync after a change.
    val artworkVersion: String? = null,
    // When the song was added: the server's date for a remote song, otherwise stamped on first import. Kept through later
    // updates, unlike [lastModified], which moves with every tag edit. Null for a song that isn't in the library, or
    // from a provider that has no date for it.
    val dateAdded: Instant? = null,
    // When the song was made a favourite (the player's heart, or "Add to Favorites"); null when it isn't one.
    val favouritedAt: Instant? = null
) {
    val isFavourite: Boolean
        get() = favouritedAt != null

    val type: Type
        get() {
            return when {
                path.contains("audiobook", true) || path.endsWith("m4b", true) -> Type.Audiobook
                path.contains("podcast", true) -> Type.Podcast
                else -> Type.Audio
            }
        }

    val albumArtistGroupKey: AlbumArtistGroupKey by lazy { albumArtistGroupKeyOf(albumArtist, artists) }

    val albumGroupKey by lazy { albumGroupKeyOf(album, albumArtist, artists) }

    enum class Type {
        Audio,
        Audiobook,
        Podcast
    }

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

    fun canBeDeleted(): Boolean = externalId == null

    /**
     * False for a file opened from another app that isn't in the library: it plays as a transient song with a
     * negative id, so there's no row to save it against (in a playlist, the saved queue, and so on).
     */
    val isInLibrary: Boolean
        get() = id >= 0
}

/**
 * The album artist group key of a song tagged [albumArtist] and [artists]: the one definition, which [Song] and anything
 * grouping raw song columns (a SQL aggregate's rows) share, so both name the same album artist.
 */
fun albumArtistGroupKeyOf(
    albumArtist: String?,
    artists: List<String>
): AlbumArtistGroupKey = AlbumArtistGroupKey(
    albumArtist?.lowercase()?.removeArticles()
        ?: artists.joinToString(", ") { it.lowercase().removeArticles() }.ifEmpty { null }
)

/** The album group key of a song tagged [album], [albumArtist] and [artists]; see [albumArtistGroupKeyOf]. */
fun albumGroupKeyOf(
    album: String?,
    albumArtist: String?,
    artists: List<String>
): AlbumGroupKey = AlbumGroupKey(album?.lowercase()?.removeArticles(), albumArtistGroupKeyOf(albumArtist, artists))
