package com.simplecityapps.mediaprovider

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaImporter(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferenceManager: GeneralPreferenceManager
) : SongImportStateProvider {
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
            Timber.v("Import failed, media providers empty")
            return
        }

        rescanRequested.set(true)

        var failure: Exception? = null
        // Checked again after each unlock: a request made between the last check and the unlock found the lock still held,
        // so whichever import sees it next runs it
        while (rescanRequested.get()) {
            if (!importLock.tryLock()) {
                Timber.v("Import already in progress, requesting a follow-up pass")
                break
            }
            try {
                while (rescanRequested.getAndSet(false)) {
                    failure =
                        try {
                            importAll()
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Only a request made during this pass runs another, so a failing import doesn't loop
                            Timber.e(e, "Import failed")
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
        Timber.v("Starting import..")
        val time = System.currentTimeMillis()

        mediaProviders.forEach { mediaProvider ->
            _songImportState.value = SongImportState.ImportProgress(mediaProvider.type, message = null, progress = null)
        }

        withContext(Dispatchers.IO) {
            mediaProviders.map { mediaProvider ->
                async {
                    importSongs(mediaProvider).collect { event ->
                        when (event) {
                            is FlowEvent.Progress -> {
                                _songImportState.value = SongImportState.ImportProgress(mediaProvider.type, event.data.message, event.data.progress)
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
                        if (event is FlowEvent.Failure) Timber.w("${mediaProvider.type} playlist import failed: ${event.message}")
                    }
                }
            }.awaitAll()
        }

        preferenceManager.lastMediaImportDate = Date()

        importCount++

        Timber.v("Import complete in ${System.currentTimeMillis() - time}ms)")
    }

    data class SongImportResult(
        val mediaProviderType: MediaProviderType,
        val inserts: Int,
        val updates: Int,
        val deletes: Int
    )

    private fun importSongs(mediaProvider: MediaProvider): Flow<FlowEvent<SongImportResult, MessageProgress>> = flow {
        emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_import_retrieving_songs), null)))

        val storedSongs = songRepository.loadSongs(SongQuery.All(includeExcluded = true, providerType = mediaProvider.type))

        val existingSongs =
            try {
                remapLegacySongs(mediaProvider, storedSongs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diffing without the remap would delete the songs it failed to move, and their history with them
                Timber.e(e, "Failed to remap legacy songs")
                emit(FlowEvent.Failure(context.getString(R.string.media_import_error)))
                return@flow
            }

        mediaProvider.findSongs(existingSongs).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(
                        FlowEvent.Progress<SongImportResult, MessageProgress>(
                            MessageProgress(
                                message = event.data.message,
                                progress = event.data.progress
                            )
                        )
                    )
                }

                is FlowEvent.Success -> {
                    try {
                        emit(FlowEvent.Progress<SongImportResult, MessageProgress>(MessageProgress(context.getString(R.string.media_import_updating_database), null)))
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
                        Timber.e(e, "Failed to update song repository")
                        emit(FlowEvent.Failure(context.getString(R.string.media_import_error)))
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
        Timber.i("Moved ${paths.size} of ${remaps.size} matched ${mediaProvider.type} songs to their new paths")
        return songs.map { song -> paths[song.id]?.let { path -> song.copy(path = path) } ?: song }
    }

    data class PlaylistImportResult(
        val mediaProviderType: MediaProviderType
    )

    private fun importPlaylists(mediaProvider: MediaProvider): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> = flow {
        emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_import_retrieving_playlists), null)))

        val existingPlaylists =
            playlistRepository.getPlaylists(query = PlaylistQuery.All(mediaProviderType = mediaProvider.type))
                .filterNotNull()
                .firstOrNull()
                .orEmpty()

        // Straight from the database: the songs this pass just stored (or the last pass did) may not be in the shared list yet
        val existingSongs = songRepository.loadSongs(SongQuery.All(includeExcluded = true, providerType = mediaProvider.type))

        mediaProvider.findPlaylists(existingPlaylists, existingSongs).collect { event ->
            when (event) {
                is FlowEvent.Progress -> {
                    emit(FlowEvent.Progress<PlaylistImportResult, MessageProgress>(event.data))
                }

                is FlowEvent.Success -> {
                    event.result.forEachIndexed { i, playlistUpdateData ->
                        emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_import_updating_database), Progress(i, event.result.size))))
                        createOrUpdatePlaylist(playlistUpdateData, existingPlaylists)
                    }
                }

                is FlowEvent.Failure -> {
                    emit(event)
                }
            }
        }
        emit(FlowEvent.Success(PlaylistImportResult(mediaProvider.type)))
    }

    data class PlaylistUpdateData(
        val mediaProviderType: MediaProviderType,
        val name: String,
        val songs: List<Song>,
        val externalId: String?
    )

    private suspend fun createOrUpdatePlaylist(
        playlistUpdateData: PlaylistUpdateData,
        existingPlaylists: List<Playlist>
    ) {
        val existingPlaylist =
            existingPlaylists.find { playlist ->
                playlist.mediaProvider == playlistUpdateData.mediaProviderType && (playlist.name == playlistUpdateData.name || playlist.externalId == playlistUpdateData.externalId)
            }

        var songsToInsert = playlistUpdateData.songs
        if (songsToInsert.isNotEmpty()) {
            if (existingPlaylist == null) {
                playlistRepository.createPlaylist(
                    playlistUpdateData.name,
                    playlistUpdateData.mediaProviderType,
                    songsToInsert,
                    playlistUpdateData.externalId
                )
            } else {
                // Update possibly stale values
                playlistRepository.renamePlaylist(existingPlaylist, playlistUpdateData.name)
                playlistRepository.updatePlaylistMediaProviderType(existingPlaylist, playlistUpdateData.mediaProviderType)
                playlistRepository.updatePlaylistExternalId(existingPlaylist, playlistUpdateData.externalId)

                // Look for duplicates
                val existingSongs =
                    playlistRepository.getSongsForPlaylist(existingPlaylist)
                        .firstOrNull()
                        .orEmpty()
                        .map { it.song }
                songsToInsert = songsToInsert.filterNot { songToInsert -> existingSongs.any { existingSong -> existingSong.id == songToInsert.id } }
                if (songsToInsert.isNotEmpty()) {
                    Timber.v("Adding ${songsToInsert.size} songs to playlist")
                    playlistRepository.addToPlaylist(existingPlaylist, songsToInsert)
                } else {
                    Timber.v("Failed to update playlist: songs empty")
                }
            }
        } else {
            Timber.v("No songs to insert")
        }
    }
}
