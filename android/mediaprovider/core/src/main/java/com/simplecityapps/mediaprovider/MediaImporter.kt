package com.simplecityapps.mediaprovider

import android.content.Context
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.Date

class MediaImporter(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferenceManager: GeneralPreferenceManager
) {

    interface Listener {
        fun onStart(providerType: MediaProviderType) {}

        fun onSongImportProgress(
            providerType: MediaProviderType,
            message: String,
            progress: Progress?
        )

        fun onSongImportComplete(providerType: MediaProviderType) {}
        fun onSongImportFailed(providerType: MediaProviderType, message: String?) {}

        fun onPlaylistImportProgress(
            providerType: MediaProviderType,
            message: String,
            progress: Progress?
        ) {
        }

        fun onPlaylistImportComplete(providerType: MediaProviderType) {}
        fun onPlaylistImportFailed(providerType: MediaProviderType, message: String?) {}

        fun onAllComplete() {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    val mediaProviders: MutableSet<MediaProvider> = mutableSetOf()

    var importCount: Int = 0

    suspend fun import() {

        if (mediaProviders.isEmpty()) {
            Timber.v("Import failed, media providers empty")
            return
        }

        if (isImporting) {
            Timber.v("Import already in progress")
            return
        }

        Timber.v("Starting import..")
        val time = System.currentTimeMillis()

        isImporting = true

        mediaProviders.forEach { mediaProvider ->
            listeners.forEach { it.onStart(mediaProvider.type) }
        }

        withContext(Dispatchers.IO) {
            mediaProviders.map { mediaProvider ->
                async {
                    importSongs(mediaProvider).collect { event ->
                        withContext(Dispatchers.Main) {
                            when (event) {
                                is FlowEvent.Progress -> {
                                    listeners.forEach { listener ->
                                        listener.onSongImportProgress(
                                            providerType = mediaProvider.type,
                                            message = event.data.message,
                                            progress = event.data.progress
                                        )
                                    }
                                }
                                is FlowEvent.Success -> {
                                    listeners.forEach { listener ->
                                        listener.onSongImportComplete(
                                            providerType = mediaProvider.type
                                        )
                                    }
                                }
                                is FlowEvent.Failure -> {
                                    listeners.forEach { listener ->
                                        listener.onSongImportFailed(
                                            mediaProvider.type,
                                            event.message
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Gives the song import a chance to complete.. Otherwise, we might not yet have any existing songs to match with our playlist songs
                    delay(500)

                    importPlaylists(mediaProvider).collect { event ->
                        withContext(Dispatchers.Main) {
                            when (event) {
                                is FlowEvent.Progress -> {
                                    listeners.forEach { listener ->
                                        listener.onPlaylistImportProgress(
                                            providerType = mediaProvider.type,
                                            message = event.data.message,
                                            progress = event.data.progress
                                        )
                                    }
                                }
                                is FlowEvent.Success -> {
                                    listeners.forEach { listener ->
                                        listener.onPlaylistImportComplete(
                                            providerType = mediaProvider.type
                                        )
                                    }
                                }
                                is FlowEvent.Failure -> {
                                    listeners.forEach { listener ->
                                        listener.onPlaylistImportFailed(
                                            mediaProvider.type,
                                            event.message
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        preferenceManager.lastMediaImportDate = Date()

        listeners.forEach { listener -> listener.onAllComplete() }

        importCount++
        isImporting = false

        Timber.v("Import complete in ${System.currentTimeMillis() - time}ms)")
    }

    data class SongImportResult(
        val mediaProviderType: MediaProviderType,
        val inserts: Int,
        val updates: Int,
        val deletes: Int
    )

    private fun importSongs(mediaProvider: MediaProvider): Flow<FlowEvent<SongImportResult, MessageProgress>> {
        return flow {

            emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_import_retrieving_songs), null)))

            val existingSongs = songRepository.getSongs(
                SongQuery.All(
                    includeExcluded = true,
                    providerType = mediaProvider.type
                )
            )
                .filterNotNull()
                .firstOrNull()
                .orEmpty()

            mediaProvider.findSongs().collect { event ->
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
                            val result = songRepository.insertUpdateAndDelete(
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
    }

    data class PlaylistImportResult(
        val mediaProviderType: MediaProviderType
    )

    private fun importPlaylists(mediaProvider: MediaProvider): Flow<FlowEvent<PlaylistImportResult, MessageProgress>> {
        return flow {

            emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_import_retrieving_playlists), null)))

            val existingPlaylists = playlistRepository.getPlaylists(query = PlaylistQuery.All(mediaProviderType = mediaProvider.type))
                .filterNotNull()
                .firstOrNull()
                .orEmpty()

            val existingSongs = songRepository.getSongs(
                SongQuery.All(
                    includeExcluded = true,
                    providerType = mediaProvider.type
                )
            )
                .filterNotNull()
                .firstOrNull()
                .orEmpty()

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
        val existingPlaylist = existingPlaylists.find { playlist ->
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
                val existingSongs = playlistRepository.getSongsForPlaylist(existingPlaylist)
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
