package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.IncrementalMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.losslessBitDepth
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withFavouriteChanges
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.jellyfin.http.ArtistItem
import com.simplecityapps.provider.jellyfin.http.Item
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.QueryResult
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.musicBrainzIds
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate

class JellyfinMediaProvider(
    private val strings: ServerStrings,
    private val authenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService
) : IncrementalMediaProvider {
    private val logger = Logger.tagged("JellyfinMediaProvider")

    override val type = MediaProviderType.Jellyfin

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, since = null)

    override fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, since)

    /**
     * Every song, or with [since] only those saved on the server (added or changed) at or after it, plus those of
     * [existingSongs] whose favourite changed on the server: that doesn't change the item's DateLastSaved (#497).
     */
    private fun findSongs(
        existingSongs: List<Song>,
        since: Instant?
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        // The server keeps no time for a favourite, so one is a favourite as of the sync that found it
        val syncedAt = Clock.System.now()
        emitAll(
            queryItems(
                address = address,
                credentials = credentials,
                since = since
            ).map { event ->
                when (event) {
                    is FlowEvent.Success -> {
                        val songs = event.result.map { item -> item.toSong(syncedAt) }
                        if (since == null) {
                            FlowEvent.Success(songs, event.complete)
                        } else {
                            FlowEvent.Success(songs.withFavouriteChanges(existingSongs, favouritePaths(address, credentials)?.associateWith { syncedAt }), event.complete)
                        }
                    }

                    is FlowEvent.Progress -> {
                        FlowEvent.Progress(event.data)
                    }

                    is FlowEvent.Failure -> {
                        FlowEvent.Failure(event.message)
                    }
                }
            }
        )
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(strings, authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        when (
            val queryResult =
                authenticationManager.checkSession(
                    credentials,
                    itemsService.playlists(
                        url = address,
                        authorization = authenticationManager.authorizationHeader(credentials),
                        userId = credentials.userId
                    )
                )
        ) {
            is NetworkResult.Success<QueryResult> -> {
                val updateData = findSongsForPlaylists(address, credentials, queryResult.body.items, existingSongs).toList()
                emit(FlowEvent.Success(updateData))
            }

            is NetworkResult.Failure -> {
                logger.error(queryResult.error) { queryResult.error.userDescription() }
                emit(FlowEvent.Failure(queryResult.error.userDescription()))
            }
        }
    }

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?.let { cachedCredentials -> authenticationManager.refreshDownloadPermission(address, cachedCredentials) }
        ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials ->
                authenticationManager.authenticate(
                    address,
                    loginCredentials
                ).getOrNull()
            }

    /** The paths of the user's favourite songs, or null if they couldn't be fetched. */
    private suspend fun favouritePaths(
        address: String,
        credentials: AuthenticatedCredentials
    ): Set<String>? {
        val event =
            pagedFlow(key = Item::id) { offset, limit ->
                authenticationManager.checkSession(
                    credentials,
                    itemsService.favouriteAudioItems(
                        url = address,
                        authorization = authenticationManager.authorizationHeader(credentials),
                        userId = credentials.userId,
                        limit = limit,
                        startIndex = offset
                    )
                ).map { it.toPage() }
            }.last()
        return (event as? FlowEvent.Success)?.result?.mapTo(HashSet()) { item -> item.songPath }
    }

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        since: Instant?
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = pagedFlow(key = Item::id) { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.audioItems(
                url = address,
                authorization = authenticationManager.authorizationHeader(credentials),
                userId = credentials.userId,
                limit = limit,
                startIndex = offset,
                minDateLastSaved = since
            )
        ).map { it.toPage() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun findSongsForPlaylists(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistItems: List<Item>,
        existingSongs: List<Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = playlistItems
        .asFlow()
        .flatMapConcat { playlistItem ->
            queryPlaylistItems(address, credentials, playlistItem.id)
                .map { event ->
                    when (event) {
                        is FlowEvent.Success -> {
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = playlistItem.name ?: strings.unknownName,
                                songs = event.result.mapNotNull { item -> existingSongs.firstOrNull { it.externalId == item.id } },
                                externalId = playlistItem.id
                            )
                        }

                        is FlowEvent.Failure -> null

                        is FlowEvent.Progress -> null
                    }
                }
        }
        .filterNotNull()

    private fun queryPlaylistItems(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistId: String
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = pagedFlow { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.playlistItems(
                url = address,
                authorization = authenticationManager.authorizationHeader(credentials),
                playlistId = playlistId,
                limit = limit,
                startIndex = offset,
                userId = credentials.userId
            )
        ).map { it.toPage() }
    }
}

private fun QueryResult.toPage() = Page(items, totalRecordCount)

/** [syncedAt] is when the sync that read the item started: a favourite's time, as the server keeps none. */
internal fun Item.toSong(syncedAt: Instant): Song = Song(
    id = 0,
    name = name,
    albumArtist = albumArtist,
    artists = artists.filter { it.isNotEmpty() },
    album = album,
    track = indexNumber,
    disc = parentIndexNumber,
    duration = ((runTime ?: 0) / (10 * 1000)).toInt(),
    date = productionYear?.let { year -> LocalDate(year, 1, 1) },
    genres = genres,
    path = songPath,
    size = 0,
    mimeType = "Audio/*",
    // The server has no modified date for items; DateCreated (when the song was added) is the closest, and keeps the
    // Last Modified sort meaningful rather than falling back to our own import time
    lastModified = createdAt,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = id,
    mediaProvider = MediaProviderType.Jellyfin,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = mediaStreams.firstOrNull { it.type == "Audio" }?.let { losslessBitDepth(it.codec, it.bitDepth) },
    sampleRate = null,
    channelCount = null,
    // Artwork for songs and albums is the album's primary image
    artworkVersion = albumPrimaryImageTag,
    // When the song was added to the server, so a fresh sign-in or re-import doesn't make the whole library new
    dateAdded = createdAt,
    // Jellyfin splits the file's ARTIST tag into Artists itself, so the raw credit string and COMPILATION aren't sent
    albumArtists = albumArtists.mapNotNull { artist -> artist.name?.takeIf(String::isNotBlank) },
    artistsTag = artists.filter { it.isNotEmpty() },
    artistDisplay = null,
    compilation = null,
    // MusicBrainzTrack is the release track id; a file's MUSICBRAINZ_TRACKID is the recording
    mbTrackId = musicBrainzIds(providerIds["MusicBrainzRecording"]).firstOrNull(),
    mbAlbumId = musicBrainzIds(providerIds["MusicBrainzAlbum"]).firstOrNull(),
    mbReleaseGroupId = musicBrainzIds(providerIds["MusicBrainzReleaseGroup"]).firstOrNull(),
    mbArtistIds = musicBrainzIds(providerIds["MusicBrainzArtist"]),
    mbAlbumArtistIds = musicBrainzIds(providerIds["MusicBrainzAlbumArtist"]),
    serverAlbumId = albumId,
    serverArtistIds = artistItems.mapNotNull(ArtistItem::id),
    serverAlbumArtistIds = albumArtists.mapNotNull(ArtistItem::id),
    favouritedAt = syncedAt.takeIf { userData?.isFavorite == true }
)

internal val Item.songPath: String
    get() = "jellyfin://item/$id"

private val Item.createdAt: Instant?
    get() = dateCreated?.let { date -> runCatching { Instant.parse(date) }.getOrNull() }
