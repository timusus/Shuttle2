package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import timber.log.Timber
import javax.inject.Inject

class TimberInitializer @Inject constructor(
    private val debugLoggingTree: DebugLoggingTree,
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {

    override fun init(application: Application) {

        // Todo: Disable for release builds
        Timber.plant(debugLoggingTree)

        if (!BuildConfig.DEBUG && preferenceManager.crashReportingEnabled) {
            Timber.plant(CrashReportingTree())
        }
    }
}

class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            Crashlytics.log(priority, tag, message)

            t?.let { throwable ->
                Crashlytics.logException(throwable)
            }
        } catch (error: Exception) {
            Log.e("TimberInit", "Failed to log to CrashReportingTree. \n[ tag: $tag\nmessage: $message. ]", error)
        }
    }
}