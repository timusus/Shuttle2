package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.util.Log
import javax.inject.Inject

class AppInitializers @Inject constructor(
    private val initializers: Set<@JvmSuppressWildcards AppInitializer>
) {
    fun init(application: Application) {
        initializers
            .sortedByDescending { it.priority() }
            .forEach {
                Log.i("AppInit", "Initialising ${it::class.java.simpleName}")
                it.init(application)
            }
    }
}
