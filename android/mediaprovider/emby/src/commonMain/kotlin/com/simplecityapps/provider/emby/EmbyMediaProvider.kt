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
import com.simplecityapps.mediaprovider.server.atStoredPrecision
import com.simplecityapps.mediaprovider.server.concatenated
import com.simplecityapps.mediaprovider.server.isMusicLibrary
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.parseServerInstant
import com.simplecityapps.mediaprovider.server.withFavouriteChanges
import com.simplecityapps.mediaprovider.server.withPlayedSongs
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
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
     * [existingSongs] whose favourite changed on the server: that doesn't change the item's DateLastSaved (#497), and those
     * played on the server since (in any client), which doesn't either. Only the libraries that may hold music are read: an
     * audiobook library holds `Audio` items too (#845).
     */
    private fun findSongs(
        existingSongs: List<Song>,
        since: Instant?
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        // The server keeps no time for a favourite, so one is a favourite as of the sync that found it
        val syncedAt = Clock.System.now().atStoredPrecision()
        withMusicLibraries(address, session) { libraries ->
            // The plays come from every library, so those of songs stored are kept: a new song is in the listing already
            val storedPaths = existingSongs.mapTo(HashSet()) { song -> song.path }
            emitAll(
                concatenated(libraries.map { libraryId -> queryItems(address = address, session = session, libraryId = libraryId, since = since, syncedAt = syncedAt) }, key = Song::path).map { event ->
                    if (event is FlowEvent.Success && since != null) {
                        val played = playedSince(address, session, since, syncedAt)?.filter { song -> song.path in storedPaths }
                        FlowEvent.Success(event.result.withPlayedSongs(played).withFavouriteChanges(existingSongs, favouritePaths(address, session)?.associateWith { syncedAt }), event.missing)
                    } else {
                        event
                    }
                }
            )
        }
    }

    override suspend fun countSongs(): Int? = (
        withServerSession<AuthenticatedCredentials, Int>(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
            withMusicLibraries(address, session) { libraries ->
                var total = 0
                for (libraryId in libraries) {
                    // One song asked for, for the total that comes with it
                    val result = session.request { credentials -> authenticationManager.checkSession(credentials, itemsService.audioIds(address, credentials.accessToken, credentials.userId, libraryId, limit = 1)) }
                    if (result !is NetworkResult.Success<QueryResult>) {
                        logger.warn { "Couldn't count the songs: ${(result as NetworkResult.Failure).error.userDescription()}" }
                        return@withMusicLibraries
                    }
                    total += result.body.totalRecordCount
                }
                emit(FlowEvent.Success(total))
            }
        }.lastOrNull() as? FlowEvent.Success
        )?.result

    override fun findSongPaths(): Flow<FlowEvent<List<String>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        withMusicLibraries(address, session) { libraries ->
            emitAll(concatenated(libraries.map { libraryId -> queryPaths(address, session, libraryId) }, key = { path -> path }))
        }
    }

    /**
     * [read]s the ids of the user's libraries that may hold music ([isMusicLibrary]). A library listing that fails, or that
     * has none of those (audiobooks and films alone, say), fails: read as no songs, it would delete every song stored.
     */
    private suspend fun <T> FlowCollector<FlowEvent<T, MessageProgress>>.withMusicLibraries(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        read: suspend FlowCollector<FlowEvent<T, MessageProgress>>.(List<String>) -> Unit
    ) {
        val libraries =
            session.request { credentials ->
                authenticationManager.checkSession(credentials, itemsService.libraries(address, credentials.accessToken, credentials.userId))
            }.map { result -> result.items.filter { library -> isMusicLibrary(library.collectionType) }.map(Item::id) }
        when (libraries) {
            is NetworkResult.Success<List<String>> -> {
                if (libraries.body.isEmpty()) {
                    logger.error { "Found no library that may hold music" }
                    emit(FlowEvent.Failure(strings.musicLibraryMissing))
                } else {
                    read(libraries.body)
                }
            }

            is NetworkResult.Failure -> {
                logger.error(libraries.error) { libraries.error.userDescription() }
                emit(FlowEvent.Failure(libraries.error.userDescription()))
            }
        }
    }

    private fun queryPaths(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        libraryId: String
    ): Flow<FlowEvent<List<String>, MessageProgress>> = pagedFlow(key = Item::id, convert = { item -> item.songPath }) { offset, limit ->
        session.request { credentials ->
            authenticationManager.checkSession(
                credentials,
                itemsService.audioIds(
                    url = address,
                    token = credentials.accessToken,
                    userId = credentials.userId,
                    parentId = libraryId,
                    limit = limit,
                    startIndex = offset
                )
            )
        }.map { it.toPage() }
    }

    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
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
                val playlists = queryResult.body.items
                // The listing isn't paged: one that left playlists out can't have them taken as gone from the server
                emit(FlowEvent.Success(findSongsForPlaylists(address, session, playlists, existingSongs, knownVersions), missing = (queryResult.body.totalRecordCount - playlists.size).coerceAtLeast(0)))
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

    /**
     * The songs last played at or after [since], or null if they couldn't be fetched. Pages through the user's played songs,
     * most recent first, and stops at the first one played before [since]: a sync reads only the plays since the last one.
     */
    private suspend fun playedSince(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        since: Instant,
        syncedAt: Instant
    ): List<Song>? {
        val played = mutableListOf<Song>()
        var offset = 0
        while (true) {
            val result =
                session.request { credentials ->
                    authenticationManager.checkSession(
                        credentials,
                        itemsService.playedAudioItems(
                            url = address,
                            token = credentials.accessToken,
                            userId = credentials.userId,
                            limit = PLAYED_PAGE_SIZE,
                            startIndex = offset
                        )
                    )
                }
            val page =
                when (result) {
                    is NetworkResult.Success<QueryResult> -> result.body

                    is NetworkResult.Failure -> {
                        logger.warn { "Couldn't read the songs played since $since: ${result.error.userDescription()}" }
                        return null
                    }
                }
            val songs = page.items.map { item -> item.toSong(syncedAt) }
            val recent = songs.takeWhile { song -> song.lastPlayed?.let { it >= since } == true }
            played += recent
            offset += page.items.size
            if (recent.size < songs.size || page.items.isEmpty() || offset >= page.totalRecordCount) return played
        }
    }

    private fun queryItems(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        libraryId: String,
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
                    parentId = libraryId,
                    limit = limit,
                    startIndex = offset,
                    minDateLastSaved = since
                )
            )
        }.map { it.toPage() }
    }

    /**
     * A playlist per one of [playlistItems], holding the library songs its items refer to, in playlist order. A playlist whose
     * items fail to load, or come to fewer than the server counts for it, is listed as [unread][MediaImporter.PlaylistListing.unread],
     * so the one stored from it is never deleted; the songs a short one did return are still added to it. One whose
     * [playlistVersion] is its version in [knownVersions] isn't read: it's [unchanged][MediaImporter.PlaylistListing.unchanged].
     */
    private suspend fun findSongsForPlaylists(
        address: String,
        session: ServerSession<AuthenticatedCredentials>,
        playlistItems: List<Item>,
        existingSongs: List<Song>,
        knownVersions: Map<String, String>
    ): MediaImporter.PlaylistListing {
        val songsById = existingSongs.filter { it.externalId != null }.associateBy { it.externalId }
        val playlists = mutableListOf<MediaImporter.PlaylistUpdateData>()
        val unread = mutableSetOf<String>()
        val unchanged = mutableSetOf<String>()
        val versions = mutableMapOf<String, String>()
        for (playlistItem in playlistItems) {
            val version = playlistItem.playlistVersion
            if (version != null && knownVersions[playlistItem.id] == version) {
                unchanged += playlistItem.id
                versions[playlistItem.id] = version
                continue
            }
            val event = queryPlaylistItems(address, session, playlistItem.id).last()
            if (event is FlowEvent.Success) {
                playlists +=
                    MediaImporter.PlaylistUpdateData(
                        mediaProviderType = type,
                        name = playlistItem.name ?: strings.unknownName,
                        songs = event.result.mapNotNull { item -> songsById[item.id] },
                        externalId = playlistItem.id
                    )
            }
            if (event !is FlowEvent.Success || !event.complete) {
                unread += playlistItem.id
            } else if (version != null) {
                versions[playlistItem.id] = version
            }
        }
        return MediaImporter.PlaylistListing(playlists, unread, unchanged, versions)
    }

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

/** Small, as a sync usually stops on the first page: few songs are played between two syncs. */
private const val PLAYED_PAGE_SIZE = 100

/**
 * What changes when a playlist is renamed or its items edited: the server saves it again. Its item count too, so an edit
 * that somehow kept the save date still shows. Null when the server didn't send the save date: the playlist is always read.
 */
internal val Item.playlistVersion: String?
    get() = dateLastSaved?.let { saved -> "$saved/${childCount ?: ""}" }

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
        lastPlayed = parseServerInstant(userData?.lastPlayedDate),
        lastCompleted = null,
        playCount = userData?.playCount?.coerceAtLeast(0) ?: 0,
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
    get() = "$SONG_PATH_PREFIX$id"

/** What [Item.songPath] puts before the item id. */
internal const val SONG_PATH_PREFIX = "emby://item/"

private val Item.createdAt: Instant?
    get() = parseServerInstant(dateCreated)
