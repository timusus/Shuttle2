package com.simplecityapps.mediaprovider

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

class MediaImporter(
    context: Context,
    private val songRepository: SongRepository,
    private val coroutineScope: CoroutineScope
) {

    interface Listener {
        fun onProgress(progress: Float, song: Song)
        fun onComplete() {}
    }

    var isImporting = false

    var listeners = mutableSetOf<Listener>()

    private var mediaProvider: MediaProvider? = null

    var importCount: Int = 0

    private val contentObserver = ScanningContentObserver(Handler(Looper.getMainLooper()))

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
    }

    fun import(mediaProvider: MediaProvider) {
        if (isImporting && mediaProvider == this.mediaProvider) {
            throw Exception("Import already in progress")
        }

        Timber.v("Scanning media...")
        val time = System.currentTimeMillis()

        isImporting = true
        importCount++

        this.mediaProvider = mediaProvider

        coroutineScope.launch {
            val songs = mutableListOf<Song>()
            mediaProvider.findSongs().collect { (song, progress) ->
                songs.add(song)
                listeners.forEach { listener -> listener.onProgress(progress, song) }
            }

            songRepository.insert(songs)
            isImporting = false

            Timber.i("Populated media in ${"%.1f".format((System.currentTimeMillis() - time) / 1000f)}s")
            listeners.forEach { listener -> listener.onComplete() }
        }
    }

    fun stopImport() {
        isImporting = false
    }

    /**
     * Re-imports all media, using the previously-set [mediaProvider] (if set).
     */
    fun reImport() {
        mediaProvider?.let { mediaProvider -> import(mediaProvider) }
    }

    inner class ScanningContentObserver(private val handler: Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                mediaProvider?.let { songProvider -> import(songProvider) }
            }, 10 * 1000)
        }
    }
}