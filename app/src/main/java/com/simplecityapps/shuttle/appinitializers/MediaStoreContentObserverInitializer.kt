package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.MediaImporter
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

class MediaStoreContentObserverInitializer @Inject constructor(
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope,
    private val mediaImporter: MediaImporter
) : ContentObserver(null), AppInitializer {

    override fun init(application: Application) {
        application.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, this)
    }

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                delay(10 * 1000)
                Timber.i("Reimporting media due to MediaStore content observer change")
                mediaImporter.import()
            }
        }
    }
}