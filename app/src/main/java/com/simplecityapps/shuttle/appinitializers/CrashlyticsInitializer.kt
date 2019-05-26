package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.simplecityapps.shuttle.BuildConfig
import io.fabric.sdk.android.Fabric
import javax.inject.Inject

class CrashlyticsInitializer @Inject constructor(
) : AppInitializer {

    override fun init(application: Application) {

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