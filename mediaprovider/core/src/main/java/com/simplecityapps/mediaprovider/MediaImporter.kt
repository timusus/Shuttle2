package com.simplecityapps.mediaprovider

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MediaImporter(
    context: Context,
    private val songRepository: SongRepository
) {

    interface Listener {
        fun onProgress(progress: Float, message: String)
        fun onComplete() {}
    }

    var isScanning = false

    var listeners = mutableSetOf<Listener>()

    private var disposable: Disposable? = null

    private var songProvider: SongProvider? = null

    var scanCount: Int = 0

    private val contentObserver = ScanningContentObserver(Handler(Looper.getMainLooper()))

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
    }

    fun startScan(songProvider: SongProvider) {

        if (isScanning && songProvider == this.songProvider) {
            return
        }

        disposable?.dispose()

        Timber.v("Scanning media...")

        isScanning = true
        scanCount++

        this.songProvider = songProvider

        val time = System.currentTimeMillis()

        disposable = songRepository.populate(songProvider) { progress, message ->
                listeners.forEach { it.onProgress(progress, message) }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Timber.i("Populated media in ${"%.1f".format((System.currentTimeMillis() - time) / 1000f)}s")
                    isScanning = false
                    listeners.forEach { listener -> listener.onComplete() }
                },
                { throwable ->
                    isScanning = false
                    listeners.forEach { listener -> listener.onComplete() }
                    Timber.e(throwable, "Failed to populate songs")
                }
            )
    }

    fun stopScan() {
        isScanning = false
        disposable?.dispose()
    }

    fun rescan() {
        songProvider?.let { songProvider -> startScan(songProvider) }
    }

    inner class ScanningContentObserver(private val handler: Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                songProvider?.let { songProvider -> startScan(songProvider) }
            }, 10 * 1000)
        }
    }
}