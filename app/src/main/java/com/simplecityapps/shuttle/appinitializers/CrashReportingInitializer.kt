package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.util.Log
import com.bugsnag.android.Bugsnag
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import timber.log.Timber
import javax.inject.Inject

class BugsnagInitializer @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {

    override fun init(application: Application) {
        if (preferenceManager.crashReportingEnabled) {
            Bugsnag.start(application)

            if (!BuildConfig.DEBUG) {
                Timber.plant(CrashReportingTree())
            }
        }
    }

    override fun priority(): Int {
        return 1
    }
}


class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        Bugsnag.leaveBreadcrumb(message)

        try {
            throwable?.let {
                Bugsnag.notify(throwable)
            }
        } catch (error: Exception) {
            Log.e("TimberInit", "Failed to log to CrashReportingTree. \n[ tag: $tag\nmessage: $message. ]", error)
        }
    }
}