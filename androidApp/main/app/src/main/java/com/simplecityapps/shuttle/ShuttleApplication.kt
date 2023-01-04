package com.simplecityapps.shuttle

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ShuttleApplication : Application(), ActivityIntentProvider, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var initializers: AppInitializers

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    @AppCoroutineScope
    @Inject
    lateinit var appCoroutineScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        themeManager.setDayNightMode()

        if (preferenceManager.previousVersionCode != BuildConfig.VERSION_CODE) {
            preferenceManager.previousVersionCode = BuildConfig.VERSION_CODE
        }

        initializers.init(this)

        if (BuildConfig.DEBUG) {
            try {
                System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to enable coroutine debugging")
            }
        }
    }

    override fun onLowMemory() {
        Timber.v("onLowMemory()")
        super.onLowMemory()
    }

    // ActivityIntentProvider Implementation

    override fun provideMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java)
    }

    // WorkManager
    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
}
