package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MediaImporter(
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

    fun startScan(songProvider: SongProvider) {

        if (isScanning && songProvider == this.songProvider) return

        disposable?.dispose()

        Timber.v("Scanning for media...")

        isScanning = true
        scanCount++

        this.songProvider = songProvider

        val time = System.currentTimeMillis()

        disposable = songRepository.populate(songProvider) { progress, message ->
            listeners.forEach { it.onProgress(progress, message) }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {}
            .subscribe(
                {
                    Timber.i("Populated songs in ${System.currentTimeMillis() - time}ms")
                    isScanning = false
                    listeners.forEach { listener -> listener.onComplete() }
                },
                { throwable ->
                    isScanning = false
                    listeners.forEach { listener -> listener.onComplete() }
                    Timber.e(throwable, "Failed to populate songs")
                })
    }

    fun stopScan() {
        isScanning = false
        disposable?.dispose()
    }

    companion object {
        var scanCount: Int = 0
    }
}