package com.simplecityapps.shuttle.appinitializers

import android.app.Application

interface AppInitializer {
    fun init(application: Application)

    /**
     * Optional priority. Higher priority intiializers will be initialized earlier
     */
    fun priority(): Int {
        return -1
    }
}
