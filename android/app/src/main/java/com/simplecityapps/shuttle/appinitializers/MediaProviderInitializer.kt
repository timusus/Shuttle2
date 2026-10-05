package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.sources.DefaultMediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicPermission
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MediaProviderInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val librarySettings: LibrarySettings,
    private val mediaSources: DefaultMediaSources,
    private val monetisationAnalytics: MonetisationAnalytics,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        mediaSources.attachEnabled()
        mediaSources.scanIfNeverScanned(MusicPermission.isGranted(context))
        mediaSources.rescanIfSongTagsOutdated()

        librarySettings.migrateRescanFrequency()
        MediaImportWorker.updateWork(
            context = context,
            importFrequency = librarySettings.rescanFrequency.value,
            hasRemoteSource = mediaSources.enabledTypes.value.any { it.remote }
        )
        // Adding the first server, or removing the last, changes whether the daily sync needs a connection
        appCoroutineScope.launch {
            mediaSources.enabledTypes
                .map { types -> types.any { it.remote } }
                .distinctUntilChanged()
                .drop(1)
                .collect { hasRemoteSource ->
                    MediaImportWorker.updateWork(context, librarySettings.rescanFrequency.value, hasRemoteSource)
                }
        }

        appCoroutineScope.launch {
            mediaSources.enabledTypes.collect { types -> monetisationAnalytics.mediaSourcesChanged(types) }
        }

        // Each return to the app brings the servers up to date, at most every few minutes (#771)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = mediaSources.syncIfStale()
            }
        )
    }
}
