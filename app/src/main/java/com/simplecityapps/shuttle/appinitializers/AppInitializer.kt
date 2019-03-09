package com.simplecityapps.shuttle.appinitializers

import android.app.Application

interface AppInitializer {
    fun init(application: Application)
}