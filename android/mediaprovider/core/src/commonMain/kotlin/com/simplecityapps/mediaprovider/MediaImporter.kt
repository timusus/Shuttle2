package com.simplecityapps.mediaprovider

import androidx.annotation.VisibleForTesting
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class MediaImporter(
    private val strings: MediaImportStrings,
    private val songRepository: SongRepository,
    private val playlistStore: ImportedPlaylistStore,
    private val preferenceManager: GeneralPreferenceManager
) : SongImportStateProvider {
    private val logger = Logger.tagged("MediaImporter")

    /** Held for the length of an import, so a second [import] finds it taken and returns rather than scanning again. */
    private val importLock = Mutex()

    /** Set by every [import] before it tries [importLock], so an import already running runs one more pass once it's done. */
    private val rescanRequested = AtomicBoolean(false)

    /** Test seam: runs after the last pass has found no request pending, just before [importLock] is released. */
    @VisibleForTesting
    internal var beforeUnlock: (suspend () -> Unit)? = null

    val isImporting: Boolean get() = importLock.isLocked

    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)

    /** The running import's progress, or how the last one ended, so a collector that arrives mid-import sees where it's at. */
    override val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    val mediaProviders: MutableSet<MediaProvider> = mutableSetOf()

    var importCount: Int = 0

    suspend fun import() {
        if (mediaProviders.isEmpty()) {
            logger.debug { "Import failed, media providers empty" }
            return
        }

        rescanRequested.store(true)

        var failure: Exception? = null
        // Checked again after each unlock: a request made between the last check and the unlock found the lock still held,
        // so whichever import sees it next runs it
        while (rescanRequested.load()) {
            if (!importLock.tryLock()) {
                logger.debug { "Import already in progress, requesting a follow-up pass" }
                break
            }
            try {
                while (rescanRequested.exchange(false)) {
                    failure =
                        try {
                            importAll()
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Only a request made during this pass runs another, so a failing import doesn't loop
                            logger.error(e) { "Import failed" }
                            e
                        }
                }
                beforeUnlock?.invoke()
            } finally {
                importLock.unlock()
            }
        }
        failure?.let { throw it }
    }

    private suspend fun importAll() {
        logger.debug { "Starting import.." }
        val time = TimeSource.Monotonic.markNow()

        mediaProviders.forEach { mediaProvider ->
            val start = MessageProgress(if (mediaProvider.type.remote) ImportPhase.Connecting else ImportPhase.Fetching, progress = null)
            _songImportState.value = mediaProvider.importProgress(start)
        }

        withContext(Dispatchers.IO) {
            mediaProviders.map { mediaProvider ->
                async {
                    importSongs(mediaProvider).collect { event ->
                        when (event) {
                            is FlowEvent.Progress -> {
                                _songImportState.value = mediaProvider.importProgress(event.data)
                            }

                            is FlowEvent.Success -> {
                                _songImportState.value = SongImportState.ImportComplete(mediaProvider.type, error = null)
                            }

                            is FlowEvent.Failure -> {
                                _songImportState.value = SongImportState.ImportComplete(mediaProvider.type, event.message)
                            }
                        }
                    }
                }
            }.awaitAll()

            mediaProviders.map { mediaProvider ->
                async {
                    importPlaylists(mediaProvider).collect { event ->
                        if (event is FlowEvent.Failure) logger.warn { "${mediaProvider.type} playlist import failed: ${event.message}" }
                    }
                }
            }.awaitAll()
        }

        preferenceManager.lastMediaImportDate = Clock.System.now()

        importCount++

        logger.debug { "Import complete in ${time.elapsedNow().inWholeMilliseconds}ms)" }
    }

    /** [progress] as the import state shows it: described in the user's words, with its count. */
    private fun MediaProvider.importProgress(progress: MessageProgress) = SongImportState.ImportProgress(type, strings.describe(progress, provider = type.name), progress.progress)

    data class SongImportResult(
        val mediaProviderType: MediaProviderType,
        val inserts: Int,
        val updates: Int,
        val deletes: Int
    )

    private fun importSongs(mediaProvider: MediaProvider): Flow<FlowEvent<SongImportResult, MessageProgress>> = flow {
        val storedSongs = songRepository.loadSongs(SongQuery.All(includeExcluded = true, providerType = mediaProvider.type))

        val existingSongs =
            try {
                remapLegacySongs(mediaProvider, storedSongs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diffing without the remap would delete the songs it failed to move, and their history with them
                logger.error(e) { "Failed to remap legacy songs" }
                emit(FlowEvent.Failure(strings.importError))
                return@flow
            }

        mediaProvider.findSongs(existingSongs).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<SongImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    try {
                        emit(FlowEvent.Progress<SongImportResult, MessageProgress>(MessageProgress(ImportPhase.Saving(event.result.size), null)))
                        val songDiff = SongDiff(existingSongs, event.result).apply()
                        val result =
                            songRepository.insertUpdateAndDelete(
                                inserts = songDiff.inserts,
                                updates = songDiff.updates,
                                deletes = songDiff.deletes,
                                mediaProviderType = mediaProvider.type
                            )
                        emit(
                            FlowEvent.Success(
                                SongImportResult(
                                    inserts = result.first,
                                    updates = result.second,
                                    deletes = result.third,
                                    mediaProviderType = mediaProvider.type
                                )
                            )
                        )
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update song repository" }
                        emit(FlowEvent.Failure(strings.importError))
                    }
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Moves songs the provider stored under an old identity to their current path before the diff, so the diff updates
     * those rows rather than deleting them and inserting new ones without their history.
     */
    private suspend fun remapLegacySongs(
        mediaProvider: MediaProvider,
        songs: List<Song>
    ): List<Song> {
        val remaps = mediaProvider.remapLegacySongs(songs)
        if (remaps.isEmpty()) return songs
        val paths = songRepository.remapPaths(remaps, mediaProvider.type).associate { remap -> remap.songId to remap.path }
        logger.info { "Moved ${paths.size} of ${remaps.size} matched ${mediaProvider.type} songs to their new paths" }
        return songs.map { song -> paths[song.id]?.let { path -> song.copy(path = path) } ?: song }
    }

    data class PlaylistImportResult(
        val mediaProviderType: MediaProviderType
    )

    private fun importPlaylists(mediaProvider: MediaProvider): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> = flow {
        // Straight from the database: the songs this pass just stored (or the last pass did) may not be in the shared list yet
        val existingSongs = songRepository.loadSongs(SongQuery.All(includeExcluded = true, providerType = mediaProvider.type))

        mediaProvider.findPlaylists(existingSongs).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<PlaylistImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    event.result.forEach { playlistUpdateData ->
                        if (playlistUpdateData.songs.isNotEmpty()) {
                            playlistStore.storePlaylist(playlistUpdateData)
                        }
                    }
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
        emit(FlowEvent.Success(PlaylistImportResult(mediaProvider.type)))
    }

    /** A playlist [mediaProviderType] found, holding the [songs] of its source, which [externalId] identifies within that provider. */
    data class PlaylistUpdateData(
        val mediaProviderType: MediaProviderType,
        val name: String,
        val songs: List<Song>,
        val externalId: String
    )
}
