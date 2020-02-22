package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.fabric.sdk.android.Fabric
import javax.inject.Inject

class CrashlyticsInitializer @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {

    override fun init(application: Application) {

        if (preferenceManager.crashReportingEnabled) {
            val crashlyticsCore = CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build()

            Fabric.with(
                application,
                Crashlytics.Builder()
                    .core(crashlyticsCore)
                    .build()
            )
        }
    }
}