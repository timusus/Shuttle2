package com.simplecityapps.shuttle.appinitializers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.ActivityLifecycleCallbacksAdapter
import timber.log.Timber
import javax.inject.Inject

class CrashReportingInitializer @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {

    override fun init(application: Application) {
        if (preferenceManager.crashReportingEnabled) {
            Bugsnag.start(application)

            if (!BuildConfig.DEBUG) {
                Timber.plant(CrashReportingTree())
            }

            application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacksAdapter {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity is FragmentActivity) {
                        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                            object : FragmentManager.FragmentLifecycleCallbacks() {
                                override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                                    Bugsnag.leaveBreadcrumb(f.javaClass.simpleName, mapOf("FragmentLifecycleCallback" to "onViewCreated()"), BreadcrumbType.NAVIGATION)
                                }

                                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                                    Bugsnag.leaveBreadcrumb(f.javaClass.simpleName, mapOf("FragmentLifecycleCallback" to "onViewDestroyed()"), BreadcrumbType.NAVIGATION)
                                }
                            },
                            true
                        )
                    }
                }
            })
        }
    }

    override fun priority(): Int {
        return 1
    }
}

class CrashReportingTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        try {
            throwable?.let {
                Bugsnag.notify(throwable)
            }
        } catch (error: Exception) {
            Log.e("CrashReportingTree", "Failed to log to CrashReportingTree. \n[ tag: $tag\nmessage: $message. ]", error)
        }
    }
}
