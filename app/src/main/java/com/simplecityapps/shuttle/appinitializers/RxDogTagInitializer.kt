package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.BuildConfig
import com.uber.rxdogtag.RxDogTag
import javax.inject.Inject

class RxDogTagInitializer @Inject constructor() : AppInitializer {

    override fun init(application: Application) {
        if (BuildConfig.DEBUG) {
            RxDogTag.install()
        }
    }
}