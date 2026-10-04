package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.IncrementalMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.isLosslessCodec
import com.simplecityapps.mediaprovider.losslessBitDepth
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ServerSession
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withFavouriteChanges
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.STREAM_TYPE_AUDIO
import com.simplecityapps.provider.plex.http.Stream
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.musicBrainzIds
import kotlin.time.Clock
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

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, since = null)

    override fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, since)

    /**
     * Every track of every music section, or with [since] only those updated on the server at or after it, plus those
     * of [existingSongs] whose favourite changed on the server: rating a track doesn't change its updatedAt (#497).
     */
    private fun findSongs(
        existingSongs: List<Song>,
        since: Instant?
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        val syncedAt = Clock.System.now()
        when (val sectionsResult = session.request { credentials -> authenticationManager.checkSession(credentials, itemsService.sections(url = address, token = credentials.accessToken)) }) {
            is NetworkResult.Success<QueryResult> -> {
                // A server can hold several music libraries, whatever they're called; they're the sections of type "artist"
                val sections = sectionsResult.body.mediaContainer.directories.orEmpty().filter { it.type == "artist" }.map { it.key }
                if (sections.isEmpty()) {
                    logger.error { "Failed to find a music section" }
                    emit(FlowEvent.Failure(plexStrings.musicLibraryMissing))
                } else {
                    emitAll(
                        queryAllSections(address, session, sections, since) { metadata -> metadata.toSong(type, syncedAt) }.map { event ->
                            if (event is FlowEvent.Success && since != null) {
                                FlowEvent.Success(event.result.withFavouriteChanges(existingSongs, favourites(address, session, sections, syncedAt)), event.missing)
                            } else {
                                event
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

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        when (val playlistsResult = session.request { credentials -> authenticationManager.checkSession(credentials, itemsService.playlists(url = address, token = credentials.accessToken)) }) {
            is NetworkResult.Success<QueryResult> -> {
                val songsByPart = existingSongs.filter { it.externalId != null }.associateBy { it.externalId }
                val updateData = findSongsForPlaylists(address, session, playlistsResult.body.mediaContainer.metadata.orEmpty(), songsByPart).toList()
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
        session: ServerSession<AuthenticatedCredentials>,
        playlists: List<Metadata>,
        songsByPart: Map<String?, Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = flow {
        for (playlist in playlists) {
            val ratingKey = playlist.ratingKey ?: continue
            val event = queryPlaylistItems(address, session, ratingKey).last()
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
        session: ServerSession<AuthenticatedCredentials>,
        playlist: String
    ): Flow<FlowEvent<List<Metadata>, MessageProgress>> = pagedFlow { offset, limit ->
        session.request { credentials ->
            authenticationManager.checkSession(
                credentials,
                itemsService.playlistItems(
                    url = address,
                    token = credentials.accessToken,
                    playlist = playlist,
                    offset = offset,
                    limit = limit
                )
            )
        }.map { it.toPage() }
    }

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() }

    /** When each of the user's favourite tracks in [sections] was favourited, by song path, or null if they couldn't be fetched. */
    private suspend fun favourites(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        sections: List<String>,
        syncedAt: Instant
    ): Map<String, Instant>? {
        // Checked again here, so a server that ignored the filter can't make every track a favourite
        val event = queryAllSections(address, session, sections, since = null, favouritesOnly = true) { metadata -> metadata.favouritedAt(syncedAt)?.let { metadata.songPath to it } }.last()
        return (event as? FlowEvent.Success)?.result?.filterNotNull()?.toMap()
    }

    /**
     * Every track of every one of [sections], each [convert]ed as its page arrives, emitted as one [FlowEvent.Success] after
     * the sections' progress, missing as many as the sections' listings together. A failed section ends the flow.
     */
    private fun <R> queryAllSections(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        sections: List<String>,
        since: Instant?,
        favouritesOnly: Boolean = false,
        convert: (Metadata) -> R
    ): Flow<FlowEvent<List<R>, MessageProgress>> = flow {
        val items = mutableListOf<R>()
        var missing = 0
        for (section in sections) {
            var failed = false
            queryItems(address, session, section, since, favouritesOnly, convert).collect { event ->
                when (event) {
                    is FlowEvent.Success -> {
                        items.addAll(event.result)
                        missing += event.missing
                    }

                    is FlowEvent.Progress -> emit(FlowEvent.Progress(event.data))

                    is FlowEvent.Failure -> {
                        failed = true
                        emit(FlowEvent.Failure(event.message))
                    }
                }
            }
            if (failed) return@flow
        }
        emit(FlowEvent.Success(items, missing))
    }

    private fun <R> queryItems(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        section: String,
        since: Instant?,
        favouritesOnly: Boolean,
        convert: (Metadata) -> R
    ): Flow<FlowEvent<List<R>, MessageProgress>> = pagedFlow(key = Metadata::key, convert = convert) { offset, limit ->
        val result = session.request { credentials ->
            authenticationManager.checkSession(
                credentials,
                itemsService.items(
                    url = address,
                    token = credentials.accessToken,
                    section = section,
                    offset = offset,
                    limit = limit,
                    updatedSince = since,
                    favouritesOnly = favouritesOnly
                )
            )
        }
        when (result) {
            is NetworkResult.Success<QueryResult> -> {
                val page = result.body.toPage()
                // A favourites listing only reads each track's rating
                NetworkResult.Success(if (favouritesOnly) page else page.copy(items = withBitDepths(address, session, page.items)))
            }

            is NetworkResult.Failure -> result
        }
    }

    /**
     * [tracks] with the audio streams of those whose codec is lossless, which a listing leaves out. Fetched [BIT_DEPTH_CHUNK_SIZE]
     * to a request; a failed request is logged and leaves its tracks without a bit depth, rather than failing the sync.
     */
    private suspend fun withBitDepths(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        tracks: List<Metadata>
    ): List<Metadata> {
        val streams = mutableMapOf<String, List<Stream>>()
        tracks.filter { track -> isLosslessCodec(track.media.firstOrNull()?.audioCodec) }
            .mapNotNull { track -> track.ratingKey }
            .chunked(BIT_DEPTH_CHUNK_SIZE)
            .forEach { ratingKeys ->
                val result = session.request { credentials ->
                    authenticationManager.checkSession(credentials, itemsService.metadata(address, credentials.accessToken, ratingKeys))
                }
                when (result) {
                    is NetworkResult.Success<QueryResult> -> result.body.mediaContainer.metadata.orEmpty().forEach { full ->
                        val key = full.ratingKey ?: return@forEach
                        streams[key] = full.media.firstOrNull()?.parts?.firstOrNull()?.streams.orEmpty()
                    }

                    is NetworkResult.Failure -> logger.error(result.error) { "Failed to read bit depths: ${result.error.userDescription()}" }
                }
            }
        if (streams.isEmpty()) return tracks
        return tracks.map { track ->
            val trackStreams = streams[track.ratingKey]?.takeIf { it.isNotEmpty() } ?: return@map track
            val media = track.media.firstOrNull() ?: return@map track
            val part = media.parts.firstOrNull() ?: return@map track
            track.copy(media = listOf(media.copy(parts = listOf(part.copy(streams = trackStreams)) + media.parts.drop(1))) + track.media.drop(1))
        }
    }
}

/** How many tracks' metadata one request asks for. */
private const val BIT_DEPTH_CHUNK_SIZE = 100

private fun QueryResult.toPage() = Page(mediaContainer.metadata.orEmpty(), mediaContainer.totalSize)

/** [syncedAt] is when the sync that read the track started: a favourite's time when the server sends none. */
internal fun Metadata.toSong(
    type: MediaProviderType,
    syncedAt: Instant
): Song = Song(
    id = 0,
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
    path = songPath,
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
    bitDepth = media.firstOrNull()?.parts?.firstOrNull()?.streams?.firstOrNull { it.streamType == STREAM_TYPE_AUDIO }
        ?.let { stream -> losslessBitDepth(media.first().audioCodec ?: stream.codec, stream.bitDepth) },
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
    serverAlbumArtistIds = listOfNotNull(grandparentRatingKey),
    favouritedAt = favouritedAt(syncedAt)
    // No artworkVersion: Plex songs have no server artwork loader, only the S2 artwork API, whose cache is keyed by URL
)

internal val Metadata.songPath: String
    get() = "plex://$key"

/** A track rated 10 (5 stars) is a favourite, from when it was rated, or [syncedAt] if the server doesn't say. */
private fun Metadata.favouritedAt(syncedAt: Instant): Instant? = if (userRating == 10.0) lastRatedAt?.let(Instant::fromEpochSeconds) ?: syncedAt else null
