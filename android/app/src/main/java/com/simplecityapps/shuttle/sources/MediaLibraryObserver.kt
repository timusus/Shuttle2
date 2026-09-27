package com.simplecityapps.shuttle.sources

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Re-scans the library when MediaStore's audio changes (new downloads, tag edits from other apps, deletions).
 *
 * Registered once at startup by [com.simplecityapps.shuttle.appinitializers.MediaProviderInitializer], only when the
 * music permission is held and an import has already finished: the first scan comes from the onboarding and
 * permission-grant paths instead. Changes are debounced [DEBOUNCE_MS] so a burst of writes (an album syncing track
 * by track) imports once. [MediaImporter.import] coalesces overlapping requests through its own importLock, so a
 * change that lands mid-import just runs one more pass.
 *
 * MediaStore only covers indexed files: extra SAF trees have no observable URI (see TaglibMediaProvider's
 * findExtraDocuments), so those folders still need a manual or scheduled scan.
 */
@SingleIn(AppScope::class)
class MediaLibraryObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaImporter: MediaImporter,
    private val mediaSources: MediaSources,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {
    private var debounceJob: Job? = null

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onMediaChanged()

            override fun onChange(
                selfChange: Boolean,
                uri: Uri?
            ) = onMediaChanged()
        }

    fun startIfReady() {
        if (!MusicPermission.isGranted(context) || !mediaSources.hasScanned) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    private fun onMediaChanged() {
        debounceJob?.cancel()
        // The app scope runs on Main, like the onboarding scan: MediaImporter notifies its listeners on the
        // caller's thread, and an IO caller crashes Settings > Media's listener if the import finishes while
        // that screen is open (#386).
        debounceJob =
            appCoroutineScope.launch {
                delay(DEBOUNCE_MS)
                Timber.i("MediaStore changed, re-scanning the library")
                mediaImporter.import()
            }
    }

    companion object {
        const val DEBOUNCE_MS = 20_000L
    }
}
