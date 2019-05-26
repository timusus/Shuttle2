package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.crashlytics.android.BuildConfig
import com.crashlytics.android.Crashlytics
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import timber.log.Timber
import javax.inject.Inject

class TimberInitializer @Inject constructor(
    private val debugLoggingTree: DebugLoggingTree
) : AppInitializer {

    override fun init(application: Application) {

        Timber.plant(debugLoggingTree)

        if (BuildConfig.DEBUG) {

        } else {
            Timber.plant(CrashReportingTree())
        }
    }
}

class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Crashlytics.log(priority, tag, message)

        t?.let { throwable ->
            Crashlytics.logException(throwable)
        }
    }
}