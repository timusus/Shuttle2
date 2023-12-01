package com.simplecityapps.shuttle.appinitializers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.ActivityLifecycleCallbacksAdapter
import javax.inject.Inject
import timber.log.Timber

class CrashReportingInitializer
@Inject
constructor(
    private val preferenceManager: GeneralPreferenceManager
) : AppInitializer {
    override fun init(application: Application) {
        if (preferenceManager.crashReportingEnabled) {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)

            if (!BuildConfig.DEBUG) {
                Timber.plant(CrashReportingTree())
            }

            application.registerActivityLifecycleCallbacks(
                object : ActivityLifecycleCallbacksAdapter {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?
                    ) {
                        if (activity is FragmentActivity) {
                            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                                object : FragmentManager.FragmentLifecycleCallbacks() {
                                    override fun onFragmentViewCreated(
                                        fm: FragmentManager,
                                        f: Fragment,
                                        v: View,
                                        savedInstanceState: Bundle?
                                    ) {
                                        Firebase.crashlytics.log("onViewCreated: ${f.javaClass.simpleName}")
                                    }

                                    override fun onFragmentViewDestroyed(
                                        fm: FragmentManager,
                                        f: Fragment
                                    ) {
                                        Firebase.crashlytics.log("onViewDestroyed: ${f.javaClass.simpleName}")
                                    }
                                },
                                true
                            )
                        }
                    }
                }
            )
        }
    }

    override fun priority(): Int {
        return 1
    }
}

class CrashReportingTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        throwable: Throwable?
    ) {
        try {
            throwable?.let {
                Firebase.crashlytics.log("tag: $tag, message: $message, throwable: ${throwable.message}")
            }
        } catch (error: Exception) {
            Log.e("CrashReportingTree", "Failed to log to CrashReportingTree. \n[ tag: $tag\nmessage: $message. ]", error)
        }
    }
}
