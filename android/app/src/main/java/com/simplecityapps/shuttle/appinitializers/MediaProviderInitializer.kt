package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.ui.screens.sources.DefaultMediaSources
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaProviderInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val librarySettings: LibrarySettings,
    private val mediaSources: DefaultMediaSources
) : AppInitializer {
    override fun init(application: Application) {
        mediaSources.attachEnabled()

        MediaImportWorker.updateWork(
            context = context,
            importFrequency = librarySettings.rescanFrequency.value
        )
    }
}
