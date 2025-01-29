package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import javax.inject.Inject
import timber.log.Timber

class TimberInitializer
@Inject
constructor(
    private val debugLoggingTree: DebugLoggingTree
) : AppInitializer {
    override fun init(application: Application) {
        Timber.plant(debugLoggingTree)
    }

    override fun priority(): Int = 2
}
