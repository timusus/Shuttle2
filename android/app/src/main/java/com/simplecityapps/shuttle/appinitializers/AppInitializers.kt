package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.util.Log
import androidx.tracing.trace
import javax.inject.Inject

class AppInitializers
@Inject
constructor(
    private val initializers: Set<@JvmSuppressWildcards AppInitializer>
) {
    fun init(application: Application) {
        initializers
            .sortedByDescending { it.priority() }
            .forEach {
                val name = it::class.java.simpleName
                Log.i("AppInit", "Initialising $name")
                trace("S2 init $name") { it.init(application) }
            }
    }
}
