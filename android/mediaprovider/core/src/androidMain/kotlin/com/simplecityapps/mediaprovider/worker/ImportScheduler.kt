package com.simplecityapps.mediaprovider.worker

import android.content.Context
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Starts the library import the user asked for (a scan, Sync now), outliving the app's screens. */
interface ImportScheduler {
    fun requestFullImport(foldersChanged: Boolean)
}

@ContributesBinding(AppScope::class)
class WorkManagerImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ImportScheduler {
    override fun requestFullImport(foldersChanged: Boolean) = MediaImportWorker.enqueueFullImport(context, foldersChanged)
}
