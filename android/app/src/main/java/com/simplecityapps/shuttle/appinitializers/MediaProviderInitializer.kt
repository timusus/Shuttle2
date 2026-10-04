package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.sources.DefaultMediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import dev.zacsweers.metro.Inject

class MediaProviderInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val librarySettings: LibrarySettings,
    private val mediaSources: DefaultMediaSources
) : AppInitializer {
    override fun init(application: Application) {
        mediaSources.attachEnabled()
        mediaSources.scanIfNeverScanned(MusicPermission.isGranted(context))
        mediaSources.rescanIfSongTagsOutdated()

        librarySettings.migrateRescanFrequency()
        MediaImportWorker.updateWork(
            context = context,
            importFrequency = librarySettings.rescanFrequency.value
        )

        // Each return to the app brings the servers up to date, at most every few minutes (#771)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = mediaSources.syncIfStale()
            }
        )
    }
}
