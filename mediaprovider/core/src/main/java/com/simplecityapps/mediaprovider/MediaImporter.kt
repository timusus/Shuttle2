package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaImporter(
    private val songRepository: SongRepository
) {

    interface Listener {
        fun onProgress(progress: Float, song: Song)
        fun onComplete() {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    private var mediaProvider: MediaProvider? = null

    var importCount: Int = 0

    suspend fun import(mediaProvider: MediaProvider) {
        if (isImporting && mediaProvider == this.mediaProvider) {
            Timber.i("Import already in progress")
            return
        }

        Timber.v("Scanning media...")
        val time = System.currentTimeMillis()

        isImporting = true
        importCount++

        this.mediaProvider = mediaProvider

        try {
            withContext(Dispatchers.IO) {
                val songs = mutableListOf<Song>()
                mediaProvider.findSongs().collect { (song, progress) ->
                    songs.add(song)

                    withContext(Dispatchers.Main) {
                        listeners.forEach { listener -> listener.onProgress(progress, song) }
                    }
                }
                songRepository.insert(songs)
            }
            listeners.forEach { listener -> listener.onComplete() }
            Timber.i("Populated media in ${"%.1f".format((System.currentTimeMillis() - time) / 1000f)}s")
        } catch (e: Exception) {
            Timber.e(e, "Failed to import media")
        } finally {
            isImporting = false
        }
    }

    /**
     * Re-imports all media, using the previously-set [mediaProvider] (if set).
     */
    suspend fun reImport() {
        mediaProvider?.let { mediaProvider -> import(mediaProvider) }
    }
}