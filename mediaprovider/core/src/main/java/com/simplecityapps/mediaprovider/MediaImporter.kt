package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaImporter(
    private val songRepository: SongRepository
) {

    interface Listener {
        fun onStart(providerType: MediaProvider.Type) {}
        fun onProgress(providerType: MediaProvider.Type, progress: Int, total: Int, song: Song)
        fun onComplete(providerType: MediaProvider.Type, inserts: Int, updates: Int, deletes: Int) {}
        fun onAllComplete() {}
        fun onFail() {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    val mediaProviders: MutableSet<MediaProvider> = mutableSetOf()

    var importCount: Int = 0

    suspend fun import() {

        if (mediaProviders.isEmpty()) {
            Timber.i("Import failed, media providers empty")
            return
        }

        if (isImporting) {
            Timber.i("Import already in progress")
            return
        }


        Timber.i("Starting import..")
        val time = System.currentTimeMillis()

        isImporting = true

        mediaProviders.forEach { mediaProvider ->
            listeners.forEach { it.onStart(mediaProvider.type) }
            withContext(Dispatchers.IO) {
                val existingSongs = songRepository.getSongs(SongQuery.All(includeExcluded = true, providerType = mediaProvider.type)).first().orEmpty()
                val newSongs = mediaProvider.findSongs { song, progress, total ->
                    listeners.forEach { it.onProgress(mediaProvider.type, progress, total, song) }
                }

                newSongs?.let {
                    val songDiff = SongDiff(existingSongs, newSongs).apply()
                    Timber.i("Diff completed: $songDiff")
                    try {
                        val result = songRepository.insertUpdateAndDelete(songDiff.inserts, songDiff.updates, songDiff.deletes, mediaProvider.type)
                        withContext(Dispatchers.Main) {
                            listeners.forEach { listener -> listener.onComplete(mediaProvider.type, result.first, result.second, result.third) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update song repository")
                        withContext(Dispatchers.Main) {
                            listeners.forEach { listener -> listener.onFail() }
                        }
                    }
                } ?: run {
                    Timber.e("Failed to import songs.. new song list null")
                    withContext(Dispatchers.Main) {
                        listeners.forEach { listener -> listener.onFail() }
                    }
                }
                // Small delay between providers scanning, to allow user to view results of each scan
                if (mediaProviders.size > 1) {
                    delay(2500)
                }
            }
        }

        listeners.forEach { listener -> listener.onAllComplete() }

        importCount++
        isImporting = false
        Timber.i("Import complete in ${System.currentTimeMillis() - time}ms)")
    }
}