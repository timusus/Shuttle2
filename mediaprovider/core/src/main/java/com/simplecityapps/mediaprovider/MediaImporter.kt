package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.*

class MediaImporter(
    private val songRepository: SongRepository,
    private val preferenceManager: GeneralPreferenceManager,
    appCoroutineScope: CoroutineScope
) {

    /*
         Todo:

         Automatic scanning objectives:

         - Keep S2's database up to date with the lates state of the various media providers
         - Don't scan excessively
         - Configurable, with sane defaults.

         Configuration options:
          - Every time S2 is launched
          - Manual only

          Future options (requires running service)
          - When MediaStore changes (requires running service to be practical)
          - Schedule

          Media provider specific options:
           - Whether an automated S2 Media Provider scan should only detect newly added files

          Other options
          - Whether an import is allowed to remove data. Default: No. Warning: This can cause song & playlist data to be lost

         Notes:
         - If an import would result in clearing all media provider data, that import is cancelled
         - Importing remote media providers could result in a large data transfer
         - Importing from the S2 Media Provider uses a lot of cpu
     */

    interface Listener {
        fun onStart(providerType: MediaProvider.Type) {}
        fun onProgress(providerType: MediaProvider.Type, progress: Int, total: Int, song: Song)
        fun onComplete(providerType: MediaProvider.Type, inserts: Int, updates: Int, deletes: Int) {}
        fun onAllComplete() {}
        fun onFail(providerType: MediaProvider.Type) {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    val mediaProviders: MutableSet<MediaProvider> = mutableSetOf()

    var importCount: Int = 0

    enum class AutoImportStrategy {
        OnLaunch, OnLaunchNewFilesOnly, Manual
    }

    private val importStrategy = AutoImportStrategy.values().firstOrNull { it.name == preferenceManager.importStrategy } ?: AutoImportStrategy.Manual

    init {
        if (importStrategy == AutoImportStrategy.OnLaunch) {
            appCoroutineScope.launch {
                import()
            }
        }
    }

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
        }

        withContext(Dispatchers.IO) {
            mediaProviders.map { mediaProvider ->
                async {
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
                                listeners.forEach { listener -> listener.onFail(mediaProvider.type) }
                            }
                        }
                    } ?: run {
                        Timber.e("Failed to import songs.. new song list null")
                        withContext(Dispatchers.Main) {
                            listeners.forEach { listener -> listener.onFail(mediaProvider.type) }
                        }
                    }
                }
            }.awaitAll()
        }

        listeners.forEach { listener -> listener.onAllComplete() }

        importCount++

        preferenceManager.lastImportDate = Date()

        isImporting = false
        Timber.i("Import complete in ${System.currentTimeMillis() - time}ms)")
    }
}