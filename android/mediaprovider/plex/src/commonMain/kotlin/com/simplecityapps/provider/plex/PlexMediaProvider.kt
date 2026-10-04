package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.IncrementalMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.musicBrainzIds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate

class PlexMediaProvider(
    private val strings: ServerStrings,
    private val plexStrings: PlexStrings,
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService
) : IncrementalMediaProvider {
    override val type: MediaProviderType
        get() = MediaProviderType.Plex

    private val logger = Logger.tagged("PlexMediaProvider")

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(since = null)

    override fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(since)

    /** Every track of every music section, or with [since] only those updated on the server at or after it. */
    private fun findSongs(since: Instant?): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        when (val sectionsResult = authenticationManager.checkSession(credentials, itemsService.sections(url = address, token = credentials.accessToken))) {
            is NetworkResult.Success<QueryResult> -> {
                // A server can hold several music libraries, whatever they're called; they're the sections of type "artist"
                val sections = sectionsResult.body.mediaContainer.directories.orEmpty().filter { it.type == "artist" }.map { it.key }
                if (sections.isEmpty()) {
                    logger.error { "Failed to find a music section" }
                    emit(FlowEvent.Failure(plexStrings.musicLibraryMissing))
                } else {
                    emitAll(
                        queryAllSections(address, credentials, sections, since).map { event ->
                            when (event) {
                                is FlowEvent.Success -> FlowEvent.Success(event.result.map { metadata -> metadata.toSong(type) })
                                is FlowEvent.Progress -> FlowEvent.Progress(event.data)
                                is FlowEvent.Failure -> FlowEvent.Failure(event.message)
                            }
                        }
                    )
                }
            }

            is NetworkResult.Failure -> {
                logger.error(sectionsResult.error) { sectionsResult.error.userDescription() }
                emit(FlowEvent.Failure(sectionsResult.error.userDescription()))
            }
        }
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(strings, authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        when (val playlistsResult = authenticationManager.checkSession(credentials, itemsService.playlists(url = address, token = credentials.accessToken))) {
            is NetworkResult.Success<QueryResult> -> {
                val songsByPart = existingSongs.filter { it.externalId != null }.associateBy { it.externalId }
                val updateData = findSongsForPlaylists(address, credentials, playlistsResult.body.mediaContainer.metadata.orEmpty(), songsByPart).toList()
                emit(FlowEvent.Success(updateData))
            }

            is NetworkResult.Failure -> {
                logger.error(playlistsResult.error) { playlistsResult.error.userDescription() }
                emit(FlowEvent.Failure(playlistsResult.error.userDescription()))
            }
        }
    }

    /** A playlist per one of [playlists], holding the library songs its items refer to, by media part. A playlist whose items fail to load is left out. */
    private fun findSongsForPlaylists(
        address: String,
        credentials: AuthenticatedCredentials,
        playlists: List<Metadata>,
        songsByPart: Map<String?, Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = flow {
        for (playlist in playlists) {
            val ratingKey = playlist.ratingKey ?: continue
            val event = queryPlaylistItems(address, credentials, ratingKey).last()
            if (event is FlowEvent.Success) {
                emit(
                    MediaImporter.PlaylistUpdateData(
                        mediaProviderType = type,
                        name = playlist.title ?: strings.unknownName,
                        songs = event.result.mapNotNull { item -> songsByPart[item.media.firstOrNull()?.parts?.firstOrNull()?.key] },
                        externalId = ratingKey
                    )
                )
            }
        }
    }

    private fun queryPlaylistItems(
        address: String,
        credentials: AuthenticatedCredentials,
        playlist: String
    ): Flow<FlowEvent<List<Metadata>, MessageProgress>> = pagedFlow { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.playlistItems(
                url = address,
                token = credentials.accessToken,
                playlist = playlist,
                offset = offset,
                limit = limit
            )
        ).map { it.toPage() }
    }

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() }

    /** Every track of every one of [sections], emitted as one [FlowEvent.Success] after the sections' progress. A failed section ends the flow. */
    private fun queryAllSections(
        address: String,
        credentials: AuthenticatedCredentials,
        sections: List<String>,
        since: Instant?
    ): Flow<FlowEvent<List<Metadata>, MessageProgress>> = flow {
        val items = mutableListOf<Metadata>()
        for (section in sections) {
            var failed = false
            queryItems(address, credentials, section, since).collect { event ->
                when (event) {
                    is FlowEvent.Success -> items.addAll(event.result)

                    is FlowEvent.Progress -> emit(FlowEvent.Progress(event.data))

                    is FlowEvent.Failure -> {
                        failed = true
                        emit(FlowEvent.Failure(event.message))
                    }
                }
            }
            if (failed) return@flow
        }
        emit(FlowEvent.Success(items))
    }

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        section: String,
        since: Instant?
    ): Flow<FlowEvent<List<Metadata>, MessageProgress>> = pagedFlow { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.items(
                url = address,
                token = credentials.accessToken,
                section = section,
                offset = offset,
                limit = limit,
                updatedSince = since
            )
        ).map { it.toPage() }
    }
}

private fun QueryResult.toPage() = Page(mediaContainer.metadata.orEmpty(), mediaContainer.totalSize)

internal fun Metadata.toSong(type: MediaProviderType): Song = Song(
    id = guid.hashCode().toLong(),
    name = title,
    albumArtist = grandparentTitle,
    // Plex sends the track's own artist as originalTitle only when it differs from the album artist's
    artists = listOfNotNull(originalTitle ?: grandparentTitle),
    album = parentTitle,
    track = index ?: 0,
    disc = parentIndex ?: 0,
    duration = duration?.toInt() ?: 0,
    date = year?.let { LocalDate(it, 1, 1) },
    genres = emptyList(),
    path = "plex://$key",
    size = media.firstOrNull()?.parts?.firstOrNull()?.size ?: 0L,
    mimeType = "Audio/*",
    // When the song was added, like the Jellyfin and Emby DateCreated; updatedAt moves on every metadata refresh
    lastModified = (addedAt ?: updatedAt)?.let { seconds -> Instant.fromEpochSeconds(seconds) },
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = media.firstOrNull()?.parts?.firstOrNull()?.key,
    mediaProvider = type,
    lyrics = null,
    grouping = null,
    bitRate = media.firstOrNull()?.bitrate,
    bitDepth = null,
    sampleRate = null,
    channelCount = media.firstOrNull()?.audioChannels,
    audioCodec = media.firstOrNull()?.audioCodec,
    // When the song was added to the server, so a fresh sign-in or re-import doesn't make the whole library new
    dateAdded = addedAt?.let { seconds -> Instant.fromEpochSeconds(seconds) },
    // Plex sends one artist string per track and one album artist, neither split into several, no COMPILATION, and no
    // album or artist MusicBrainz ids on a track: only its recording id, in the Guid list
    albumArtists = listOfNotNull(grandparentTitle),
    artistsTag = listOfNotNull(originalTitle ?: grandparentTitle),
    artistDisplay = originalTitle ?: grandparentTitle,
    compilation = null,
    mbTrackId = guids.firstNotNullOfOrNull { guid -> guid.id.takeIf { it.startsWith("mbid://") }?.let { musicBrainzIds(it).firstOrNull() } },
    mbAlbumId = null,
    mbReleaseGroupId = null,
    mbArtistIds = emptyList(),
    mbAlbumArtistIds = emptyList(),
    serverAlbumId = parentRatingKey,
    // The track's own artist (originalTitle) has no id of its own; without one, the track's artist is the album's
    serverArtistIds = if (originalTitle == null) listOfNotNull(grandparentRatingKey) else emptyList(),
    serverAlbumArtistIds = listOfNotNull(grandparentRatingKey)
    // No artworkVersion: Plex songs have no server artwork loader, only the S2 artwork API, whose cache is keyed by URL
)
