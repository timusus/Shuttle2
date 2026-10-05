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
    // What a scan or tag edit reads, for the update to store. The library's songs leave it null, so the whole library's
    // lyrics aren't held in memory (#873): [com.simplecityapps.mediaprovider.repository.songs.SongRepository.loadLyrics]
    // reads one song's.
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
    val favouritedAt: Instant? = null,
    // The tags as the song's source holds them (#637): [AlbumIdentityRule] groups albums by them. Null where the source has no such value, and for a song not read since they were
    // added (the one-off backfill after the migration to version 50 fills them).
    // The ALBUMARTISTS multi-value tag (a server's album artist list), as written: never split.
    val albumArtists: List<String>? = null,
    // The ARTISTS multi-value tag (a server's artist list), as written. [artists] stays the ARTIST tag split on ';'.
    val artistsTag: List<String>? = null,
    // The raw ARTIST tag, unsplit ("A feat. B"): what a song row will show.
    val artistDisplay: String? = null,
    // COMPILATION (Vorbis), TCMP (ID3) or cpil (MP4); null when untagged.
    val compilation: Boolean? = null,
    // MusicBrainz ids: the recording (MUSICBRAINZ_TRACKID), the release, the release group, and the artists.
    val mbTrackId: String? = null,
    val mbAlbumId: String? = null,
    val mbReleaseGroupId: String? = null,
    val mbArtistIds: List<String>? = null,
    val mbAlbumArtistIds: List<String>? = null,
    // A server's own ids for the song's album, artists and album artists (Jellyfin/Emby item ids, Plex rating keys).
    val serverAlbumId: String? = null,
    val serverArtistIds: List<String>? = null,
    val serverAlbumArtistIds: List<String>? = null,
    // The album this song belongs to in its library, as [AlbumIdentityRule] decided it over the library's songs; null for
    // a song read on its own (one being imported, or opened from another app), which is then its album's only song.
    val albumIdentity: AlbumIdentity? = null
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

    /** [albumIdentity], or for a song read on its own, the identity it has as its album's only song. */
    val resolvedAlbumIdentity: AlbumIdentity by lazy { albumIdentity ?: AlbumIdentityRule.resolve(listOf(identityTags)).getValue(id) }

    val albumGroupKey: AlbumGroupKey get() = resolvedAlbumIdentity.groupKey

    val albumArtistGroupKey: AlbumArtistGroupKey get() = resolvedAlbumIdentity.albumArtistGroupKey

    /** The artists this song credits, each as the artist page it belongs to ([ArtistCredits]). */
    val artistCredits: List<ArtistCredit> by lazy { ArtistCredits.credits(identityTags, resolvedAlbumIdentity) }

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
