package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.DebugLoggingTree
import timber.log.Timber
import javax.inject.Inject

class TimberInitializer @Inject constructor(
    private val debugLoggingTree: DebugLoggingTree
) : AppInitializer {

    override fun init(application: Application) {
        if (BuildConfig.DEBUG) {
            Timber.plant(debugLoggingTree)
        }
    }
}