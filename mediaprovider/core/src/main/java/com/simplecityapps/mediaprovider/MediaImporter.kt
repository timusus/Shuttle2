package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaImporter(
    private val songRepository: SongRepository
) {

    interface Listener {
        fun onProgress(progress: Int, total: Int, song: Song)
        fun onComplete(inserts: Int, updates: Int, deletes: Int) {}
        fun onFail() {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    var mediaProvider: MediaProvider? = null

    var importCount: Int = 0

    suspend fun import() {

        if (mediaProvider == null) {
            Timber.i("Import failed, media provider null")
            return
        }

        if (isImporting) {
            Timber.i("Import already in progress")
            return
        }

        Timber.i("Starting import..")
        val time = System.currentTimeMillis()

        isImporting = true

        withContext(Dispatchers.IO) {
            val existingSongs = songRepository.getSongs(SongQuery.All(includeExcluded = true)).first()
            val newSongs = mediaProvider!!.findSongs { song, progress, total ->
                listeners.forEach { it.onProgress(progress, total, song) }
            }

            newSongs?.let {
                val songDiff = SongDiff(existingSongs, newSongs).apply()
                Timber.i("Diff completed: $songDiff")
                try {
                    val result = songRepository.insertUpdateAndDelete(songDiff.inserts, songDiff.updates, songDiff.deletes)
                    withContext(Dispatchers.Main) {
                        listeners.forEach { listener -> listener.onComplete(result.first, result.second, result.third) }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update song repository")
                    withContext(Dispatchers.Main) {
                        listeners.forEach { listener -> listener.onFail() }
                    }
                }
            } ?: run {
                Timber.e("Failed to import songs.. new song list null")
                listeners.forEach { listener -> listener.onFail() }
            }
        }
        importCount++
        isImporting = false
        Timber.i("Import complete in ${System.currentTimeMillis() - time}ms)")
    }
}