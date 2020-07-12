package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.bugsnag.android.Bugsnag
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

class BugsnagInitializer @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {

    override fun init(application: Application) {

        if (preferenceManager.crashReportingEnabled) {
            Bugsnag.start(application)
        }
    }
}