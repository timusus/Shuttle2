package com.simplecityapps.shuttle

import android.app.Application
import android.content.Intent
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.di.AppComponent
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class ShuttleApplication : Application(), ActivityIntentProvider {

    @Inject
    lateinit var initializers: AppInitializers

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    @Named("AppCoroutineScope")
    @Inject
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var widgetManager: WidgetManager

    lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        themeManager.setDayNightMode()

        if (preferenceManager.previousVersionCode != BuildConfig.VERSION_CODE) {
            // Todo: Remove sometime in future
            if (preferenceManager.previousVersionCode <= 2180018) {
                appCoroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            File(cacheDir, "image_cache").delete()
                        } catch (e: IOException) {
                            Timber.e(e, "Failed to delete disk cache")
                        }
                    }
                }
            }

            preferenceManager.previousVersionCode = BuildConfig.VERSION_CODE
        }

        initializers.init(this)

        try {
            System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to enable coroutine debugging")
        }

        widgetManager.updateAppWidgets(WidgetManager.UpdateReason.Unknown)
    }

    override fun onLowMemory() {
        Timber.v("onLowMemory()")
        super.onLowMemory()
    }


    // ActivityIntentProvider Implementation

    override fun provideMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java)
    }
}