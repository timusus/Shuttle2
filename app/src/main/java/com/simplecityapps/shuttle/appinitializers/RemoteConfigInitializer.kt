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
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.ActivityLifecycleCallbacksAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject


class RemoteConfigInitializer @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val remoteConfig: FirebaseRemoteConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope
) : AppInitializer {

    override fun init(application: Application) {
        if (preferenceManager.firebaseAnalyticsEnabled) {
            coroutineScope.launch {
                remoteConfig.fetchAndActivate().await()
            }
        }
    }

    override fun priority(): Int {
        return 1
    }
}