package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** An [importer] over [provider] with in-memory edges, shared by the `MediaImporter*Test` classes. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MediaImporterTestBase {
    protected val provider = GatedProvider()
    protected val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    protected val clock = FakeClock(Instant.parse("2026-10-04T09:00:00Z"))
    protected val songRepository = FakeSongRepository()
    protected val server = ServerProvider()
    protected val playlistStore = FakePlaylistStore()

    /** What each [MediaImporter] afterImport was told: whether every source's songs hold every tag. */
    protected val afterImports = mutableListOf<Boolean>()
    protected val importer =
        MediaImporter(
            strings = FakeMediaImportStrings,
            songRepository = songRepository,
            playlistStore = playlistStore,
            preferenceManager = preferences,
            afterImport = { songTagsCurrent -> afterImports += songTagsCurrent },
            clock = clock
        ).apply { mediaProviders += provider }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The library has been imported under this build, so a sync has nothing waiting on the first import
        preferences.lastMediaImportDate = clock.time
        preferences.songTagsRescanVersion = MediaImporter.SONG_TAGS_VERSION
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** An importer of [server] alone. */
    protected fun serverImporter() = MediaImporter(
        strings = FakeMediaImportStrings,
        songRepository = songRepository,
        playlistStore = playlistStore,
        preferenceManager = preferences,
        afterImport = {},
        clock = clock
    ).apply { mediaProviders += server }

    protected fun song(
        id: Long = 1,
        path: String = "jellyfin://item/1"
    ) = Song(
        id = id,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = null,
        dateAdded = null
    )
}

/** A server that records the time each song request asked from (null for every song) and finds no songs. */
class ServerProvider(
    override val type: MediaProviderType = MediaProviderType.Jellyfin
) : IncrementalMediaProvider {

    val requests = mutableListOf<Instant?>()
    var playlistRequests = 0

    /** The versions each playlist listing was told the stored playlists had, and the listing it answers with. */
    val knownVersionRequests = mutableListOf<Map<String, String>>()
    var playlistListing: MediaImporter.PlaylistListing? = null

    /** What it finds, or the failure it reports instead. */
    var found: List<Song> = emptyList()
    var failure: String? = null

    /** How many songs short of its total its listings come to ([FlowEvent.Success.missing]). */
    var missing = 0

    override var unreadableRoots: Set<String> = emptySet()

    /** How many songs it says it holds, or null for a count it can't read. */
    var count: Int? = null

    /** The paths it lists as held (missing [heldMissing] short of its total), or null for a path listing that fails. */
    var held: List<String>? = null
    var heldMissing = 0
    var pathListings = 0

    /** Listings returned before [held], one per request, for a library that changes between them. */
    val earlierListings = ArrayDeque<List<String>>()

    override suspend fun countSongs(): Int? = count

    override fun findSongPaths(): Flow<FlowEvent<List<String>, MessageProgress>> = flow {
        pathListings++
        val paths = earlierListings.removeFirstOrNull() ?: held
        paths?.let { emit(FlowEvent.Success(it, heldMissing)) } ?: emit(FlowEvent.Failure("The listing failed"))
    }

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since = null)

    override fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = songs(since)

    private fun songs(since: Instant?): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        requests += since
        emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, progress = null)))
        failure?.let { emit(FlowEvent.Failure(it)) } ?: emit(FlowEvent.Success(found, missing))
    }

    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = flow {
        playlistRequests++
        knownVersionRequests += knownVersions
        playlistListing?.let { emit(FlowEvent.Success(it)) }
    }
}

/** A playlist store holding the playlists of [storedIds], which records each listing it was asked to reconcile. */
class FakePlaylistStore : ImportedPlaylistStore {
    var storedIds: Set<String> = emptySet()
    val reconciled = mutableListOf<MediaImporter.PlaylistListing>()

    override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = error("ImportedPlaylistStore.storePlaylist isn't faked")

    override suspend fun storedPlaylistIds(type: MediaProviderType): Set<String> = storedIds

    override suspend fun reconcilePlaylists(
        type: MediaProviderType,
        listing: MediaImporter.PlaylistListing,
        listingComplete: Boolean,
        lastServerSongs: Map<String, Set<Long>>
    ) {
        reconciled += listing
    }
}

/** A source with an index, which records how each of its listings was asked for. */
class IndexedProvider : IndexedMediaProvider {
    override val type = MediaProviderType.Shuttle

    val listings = mutableListOf<String>()

    override var skippedFiles: Set<String> = emptySet()

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = listing("index")

    override fun findSongsThoroughly(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = listing("thorough")

    private fun listing(kind: String): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        listings += kind
        emit(FlowEvent.Success(emptyList()))
    }

    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = emptyFlow()
}

/**
 * Counts its scans, signals [started] as each one begins, holds it open until a [gate] send, then throws [failure] if [failNext]
 * is set, reports [scanFailure] if that's set, or else finds no songs.
 */
class GatedProvider(
    override val type: MediaProviderType = MediaProviderType.Shuttle
) : MediaProvider {

    val scans = AtomicInt(0)
    val stored = AtomicInt(0)
    val started = Channel<Unit>(Channel.UNLIMITED)
    val gate = Channel<Unit>(Channel.UNLIMITED)
    val failNext = AtomicBoolean(false)
    val failure = IllegalStateException("Scan failed")

    @Volatile var scanFailure: String? = null

    /** The files its last listing left unread, as a source without an index (MediaStore) reports them. */
    @Volatile override var skippedFiles: Set<String> = emptySet()

    /** What it finds, once its gate opens. */
    @Volatile var found: List<Song> = emptyList()

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        scans.incrementAndFetch()
        started.send(Unit)
        gate.receive()
        if (failNext.exchange(false)) throw failure
        emit(scanFailure?.let { message -> FlowEvent.Failure(message) } ?: FlowEvent.Success(found))
    }

    override suspend fun songsStored() {
        stored.incrementAndFetch()
    }

    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = emptyFlow()
}

class FakeClock(var time: Instant) : Clock {
    override fun now(): Instant = time
}

object FakeMediaImportStrings : MediaImportStrings {
    override fun connecting(provider: String) = "Connecting to $provider"
    override val fetching = "Fetching"
    override fun fetchingSongs(
        count: Int,
        total: Int
    ) = "Fetching $count of $total"
    override fun saving(count: Int) = "Saving $count"
    override val importError = "Import failed"
}

/** A repository whose queries all return [stored]; anything else fails the test. */
class FakeSongRepository : SongRepository {
    var stored: List<Song> = emptyList()

    /** The rows each [insertUpdateAndDelete] was asked to write. */
    val writes = mutableListOf<Int>()

    /** The songs each provider's imports deleted. */
    val deleted = mutableMapOf<MediaProviderType, List<Song>>()

    /** The inserts, updates and deletes of the last [insertUpdateAndDelete]. */
    var changes = Triple(0, 0, 0)

    override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(stored)

    override fun countSongs(): Flow<Int> = flowOf(stored.size)

    override fun countSongsByProvider(): Flow<Map<MediaProviderType, Int>> = flowOf(stored.groupingBy { it.mediaProvider }.eachCount())

    override suspend fun loadLyrics(songId: Long): String? = null

    override val updatedSongIds: Flow<Set<Long>> = flowOf(emptySet())

    override suspend fun insert(songs: List<Song>, mediaProviderType: MediaProviderType) = notFaked()

    override suspend fun update(song: Song): Int = notFaked()

    override suspend fun update(songs: List<Song>) = notFaked()

    override suspend fun remove(song: Song) = notFaked()

    override suspend fun removeAll(mediaProviderType: MediaProviderType) = notFaked()

    override suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int> {
        writes += inserts.size + updates.size + deletes.size
        deleted[mediaProviderType] = deleted[mediaProviderType].orEmpty() + deletes
        return Triple(inserts.size, updates.size, deletes.size).also { changes = it }
    }

    override suspend fun remapPaths(remaps: List<SongPathRemap>, mediaProviderType: MediaProviderType): List<SongPathRemap> = notFaked()

    override suspend fun setPlaybackPosition(song: Song, playbackPosition: Int) = notFaked()

    override suspend fun recordPlayedThrough(song: Song) = notFaked()

    override suspend fun setExcluded(songs: List<Song>, excluded: Boolean) = notFaked()

    override suspend fun clearExcludeList() = notFaked()

    override suspend fun setFavourite(songs: List<Song>, favourite: Boolean) = notFaked()

    private fun notFaked(): Nothing = error("SongRepository call isn't faked")
}
