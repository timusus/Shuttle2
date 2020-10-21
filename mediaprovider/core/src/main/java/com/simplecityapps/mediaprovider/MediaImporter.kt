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

        val existingSongs = songRepository.getSongs(SongQuery.All(includeExcluded = true)).first()

        mediaProvider!!.findSongs { song, progress, total ->
            listeners.forEach { it.onProgress(progress, total, song) }
        }?.let { newSongs ->
            val diffResult = Diff(existingSongs, newSongs).apply()
            Timber.i("Diff completed: $diffResult")
            try {
                val result = songRepository.insertUpdateAndDelete(diffResult.inserts, diffResult.updates, diffResult.deletes)
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

        importCount++
        isImporting = false
        Timber.i("Import complete in ${System.currentTimeMillis() - time}ms)")
    }


    class Diff(private val existingSongs: List<Song>, private val newSongs: List<Song>) {

        class Result(
            val inserts: List<Song>,
            val updates: List<Song>,
            val deletes: List<Song>
        ) {
            override fun toString(): String {
                return "${inserts.count()} inserts, ${updates.count()} updates, ${deletes.count()} deletes"
            }
        }

        suspend fun apply(): Result {
            if (existingSongs.isEmpty()) {
                return Result(inserts = newSongs, updates = emptyList(), deletes = emptyList())
            }

            if (newSongs.isEmpty()) {
                return Result(inserts = emptyList(), updates = emptyList(), deletes = existingSongs)
            }

            return withContext(Dispatchers.IO) {
                // Songs which exist in the new dataset, but not in the old (unique by path)
                val inserts = newSongs.filterNot { newSong -> existingSongs.any { oldSong -> oldSong.path == newSong.path } }

                // Songs which exist in the new dataset, as well as the old (unique by path)
                var updates = (newSongs - inserts)
                // Updates need their previous ID's restored
                if (updates.isNotEmpty()) {
                    updates = updates.map { newSong -> newSong.copy(id = existingSongs.first { oldSong -> oldSong.path == newSong.path }.id) }
                }

                // Songs which exist in the old dataset, but not in the new (unique by path)
                val deletes = existingSongs.filter { oldSong -> newSongs.none { newSong -> newSong.path == oldSong.path } }

                Result(inserts, updates, deletes)
            }
        }
    }
}