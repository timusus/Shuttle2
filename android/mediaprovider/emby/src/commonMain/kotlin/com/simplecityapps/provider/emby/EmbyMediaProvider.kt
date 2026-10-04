package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.IncrementalMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MessageProgress
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
import com.simplecityapps.provider.emby.http.ArtistItem
import com.simplecityapps.provider.emby.http.Item
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.QueryResult
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

class EmbyMediaProvider(
    private val strings: ServerStrings,
    private val authenticationManager: EmbyAuthenticationManager,
    private val itemsService: ItemsService
) : IncrementalMediaProvider {
    override val type = MediaProviderType.Emby

    private val logger = Logger.tagged("EmbyMediaProvider")

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
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        // The server keeps no time for a favourite, so one is a favourite as of the sync that found it
        val syncedAt = Clock.System.now()
        emitAll(
            queryItems(address = address, session = session, since = since, syncedAt = syncedAt).map { event ->
                if (event is FlowEvent.Success && since != null) {
                    FlowEvent.Success(event.result.withFavouriteChanges(existingSongs, favouritePaths(address, session)?.associateWith { syncedAt }), event.missing)
                } else {
                    event
                }
            }
        )
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        when (
            val queryResult =
                session.request { credentials ->
                    authenticationManager.checkSession(
                        credentials,
                        itemsService.playlists(
                            url = address,
                            token = credentials.accessToken,
                            userId = credentials.userId
                        )
                    )
                }
        ) {
            is NetworkResult.Success<QueryResult> -> {
                val updateData = findSongsForPlaylists(address, session, queryResult.body.items, existingSongs).toList()
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
        session: ServerSession<AuthenticatedCredentials>
    ): Set<String>? {
        val event =
            pagedFlow(key = Item::id, convert = { item -> item.songPath }) { offset, limit ->
                session.request { credentials ->
                    authenticationManager.checkSession(
                        credentials,
                        itemsService.favouriteAudioItems(
                            url = address,
                            token = credentials.accessToken,
                            userId = credentials.userId,
                            limit = limit,
                            startIndex = offset
                        )
                    )
                }.map { it.toPage() }
            }.last()
        return (event as? FlowEvent.Success)?.result?.toHashSet()
    }

    private fun queryItems(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        since: Instant?,
        syncedAt: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = pagedFlow(key = Item::id, convert = { item -> item.toSong(syncedAt) }) { offset, limit ->
        session.request { credentials ->
            authenticationManager.checkSession(
                credentials,
                itemsService.audioItems(
                    url = address,
                    token = credentials.accessToken,
                    userId = credentials.userId,
                    limit = limit,
                    startIndex = offset,
                    minDateLastSaved = since
                )
            )
        }.map { it.toPage() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun findSongsForPlaylists(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        playlistItems: List<Item>,
        existingSongs: List<Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = playlistItems
        .asFlow()
        .flatMapConcat { playlistItem ->
            queryPlaylistItems(address, session, playlistItem.id)
                .map { event ->
                    when (event) {
                        is FlowEvent.Success -> {
                            val matchingSongs = event.result.mapNotNull { item -> existingSongs.firstOrNull { item.id == it.externalId } }
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = playlistItem.name ?: strings.unknownName,
                                songs = matchingSongs,
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
        session: ServerSession<AuthenticatedCredentials>,
        playlistId: String
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = pagedFlow { offset, limit ->
        session.request { credentials ->
            authenticationManager.checkSession(
                credentials,
                itemsService.playlistItems(
                    url = address,
                    token = credentials.accessToken,
                    playlistId = playlistId,
                    limit = limit,
                    startIndex = offset,
                    userId = credentials.userId
                )
            )
        }.map { it.toPage() }
    }
}

private fun QueryResult.toPage() = Page(items, totalRecordCount)

/** [syncedAt] is when the sync that read the item started: a favourite's time, as the server keeps none. */
internal fun Item.toSong(syncedAt: Instant): Song {
    val audioStream = mediaStreams.firstOrNull { it.type == "Audio" }
    return Song(
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
        mediaProvider = MediaProviderType.Emby,
        lyrics = null,
        grouping = null,
        // Bits per second on the wire; a song stores kbps, like Plex and the local scan
        bitRate = audioStream?.bitRate?.takeIf { it > 0 }?.let { bitsPerSecond -> (bitsPerSecond + 500) / 1000 },
        bitDepth = audioStream?.let { losslessBitDepth(it.codec, it.bitDepth) },
        sampleRate = audioStream?.sampleRate?.takeIf { it > 0 },
        channelCount = audioStream?.channels?.takeIf { it > 0 },
        audioCodec = audioStream?.codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        // Artwork for songs and albums is the album's primary image
        artworkVersion = albumPrimaryImageTag,
        // When the song was added to the server, so a fresh sign-in or re-import doesn't make the whole library new
        dateAdded = createdAt,
        // Emby splits the file's ARTIST tag into Artists itself, so the raw credit string and COMPILATION aren't sent
        albumArtists = albumArtists.mapNotNull { artist -> artist.name?.takeIf(String::isNotBlank) },
        artistsTag = artists.filter { it.isNotEmpty() },
        artistDisplay = null,
        compilation = null,
        mbTrackId = musicBrainzIds(providerIds["MusicBrainzTrack"]).firstOrNull(),
        mbAlbumId = musicBrainzIds(providerIds["MusicBrainzAlbum"]).firstOrNull(),
        mbReleaseGroupId = musicBrainzIds(providerIds["MusicBrainzReleaseGroup"]).firstOrNull(),
        mbArtistIds = musicBrainzIds(providerIds["MusicBrainzArtist"]),
        mbAlbumArtistIds = musicBrainzIds(providerIds["MusicBrainzAlbumArtist"]),
        serverAlbumId = albumId,
        serverArtistIds = artistItems.mapNotNull(ArtistItem::id),
        serverAlbumArtistIds = albumArtists.mapNotNull(ArtistItem::id),
        favouritedAt = syncedAt.takeIf { userData?.isFavorite == true }
    )
}

internal val Item.songPath: String
    get() = "emby://item/$id"

private val Item.createdAt: Instant?
    get() = dateCreated?.let { date -> runCatching { Instant.parse(date) }.getOrNull() }
