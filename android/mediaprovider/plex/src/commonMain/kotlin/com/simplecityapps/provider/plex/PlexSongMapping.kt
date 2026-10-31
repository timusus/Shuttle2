package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.losslessBitDepth
import com.simplecityapps.mediaprovider.splitArtistTag
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.STREAM_TYPE_AUDIO
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.musicBrainzIds
import com.simplecityapps.shuttle.model.toTagYearDate
import kotlin.time.Instant

/**
 * What changes when a playlist is renamed or its items edited: Plex updates its updatedAt. Its item count too, so an edit
 * within the same second still shows. Null when the server sent no updatedAt: the playlist is always read.
 */
internal val Metadata.playlistVersion: String?
    get() = updatedAt?.let { updated -> "$updated/${leafCount ?: ""}" }

/** [syncedAt] is when the sync that read the track started: a favourite's time when the server sends none. */
internal fun Metadata.toSong(
    type: MediaProviderType,
    syncedAt: Instant
): Song = Song(
    id = 0,
    name = title,
    albumArtist = grandparentTitle,
    artists = trackArtists,
    album = parentTitle,
    track = index,
    disc = parentIndex,
    // A track the server hasn't finished analysing may carry its length only on its media
    duration = (duration ?: media.firstNotNullOfOrNull { it.duration })?.toInt() ?: 0,
    date = year.toTagYearDate(),
    genres = emptyList(),
    path = songPath,
    size = media.firstOrNull()?.parts?.firstOrNull()?.size ?: 0L,
    mimeType = "Audio/*",
    // When the song was added, like the Jellyfin and Emby DateCreated; updatedAt moves on every metadata refresh
    lastModified = (addedAt ?: updatedAt)?.let { seconds -> Instant.fromEpochSeconds(seconds) },
    lastPlayed = lastViewedAt?.let { Instant.fromEpochSeconds(it) },
    lastCompleted = null,
    playCount = viewCount?.coerceAtLeast(0) ?: 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = media.firstOrNull()?.parts?.firstOrNull()?.key,
    mediaProvider = type,
    lyrics = null,
    grouping = null,
    bitRate = media.firstOrNull()?.bitrate,
    bitDepth = media.firstOrNull()?.parts?.firstOrNull()?.streams?.firstOrNull { it.streamType == STREAM_TYPE_AUDIO }
        ?.let { stream -> losslessBitDepth(media.first().audioCodec ?: stream.codec, stream.bitDepth) },
    sampleRate = null,
    channelCount = media.firstOrNull()?.audioChannels,
    audioCodec = media.firstOrNull()?.audioCodec,
    // When the song was added to the server, so a fresh sign-in or re-import doesn't make the whole library new
    dateAdded = addedAt?.let { seconds -> Instant.fromEpochSeconds(seconds) },
    // Plex sends one artist string per track and one album artist, which are split here, no COMPILATION, and no
    // album or artist MusicBrainz ids on a track: only its recording id, in the Guid list
    albumArtists = splitArtistTag(grandparentTitle.orEmpty()),
    artistsTag = trackArtists,
    artistDisplay = originalTitle ?: grandparentTitle,
    compilation = null,
    mbTrackId = guids.firstNotNullOfOrNull { guid -> guid.id.takeIf { it.startsWith("mbid://") }?.let { musicBrainzIds(it).firstOrNull() } },
    mbAlbumId = null,
    mbReleaseGroupId = null,
    mbArtistIds = emptyList(),
    mbAlbumArtistIds = emptyList(),
    serverAlbumId = parentRatingKey,
    // Paired 1:1 with artistsTag, or none. The track's own artist (originalTitle) has no id of its own; without one, the
    // track's artist is the album's, whose id is the whole string's: one artist's when it names one, none of the artists
    // it's split into when it names several
    serverArtistIds = if (originalTitle == null && trackArtists.size == 1) listOfNotNull(grandparentRatingKey) else emptyList(),
    // The album artist's, unsplit like albumArtist, which the album artist's page is keyed by
    serverAlbumArtistIds = listOfNotNull(grandparentRatingKey),
    favouritedAt = favouritedAt(syncedAt)
    // No artworkVersion: Plex songs have no server artwork loader, only the S2 artwork API, whose cache is keyed by URL
)

/**
 * The track's artists. Plex sends the track's own artist as originalTitle only when it differs from the album artist's.
 * One string holds several artists when the file does, which the other sources get split.
 */
private val Metadata.trackArtists: List<String>
    get() = splitArtistTag(originalTitle ?: grandparentTitle.orEmpty())

internal val Metadata.songPath: String
    get() = "plex://$key"

/** A track rated 10 (5 stars) is a favourite, from when it was rated, or [syncedAt] if the server doesn't say. */
internal fun Metadata.favouritedAt(syncedAt: Instant): Instant? = if (userRating == 10.0) lastRatedAt?.let(Instant::fromEpochSeconds) ?: syncedAt else null
