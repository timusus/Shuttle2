package com.simplecityapps.shuttle

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.mediaprovider.repository.SongRepositoryProvider
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.widgets.WidgetManager
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.dagger.*
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.widgets.WidgetProvider41
import com.simplecityapps.shuttle.ui.widgets.WidgetProvider42
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject


class ShuttleApplication : Application(),
    HasAndroidInjector,
    ActivityIntentProvider,
    OkHttpClientProvider,
    GeneralPreferenceManagerProvider,
    SongRepositoryProvider {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var initializers: AppInitializers

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        appComponent = DaggerAppComponent
            .builder()
            .application(this)
            .build()

        appComponent.inject(this)

        AppInjector.init(this)

        themeManager.setDayNightMode()

        if (preferenceManager.previousVersionCode != BuildConfig.VERSION_CODE) {
            preferenceManager.previousVersionCode = BuildConfig.VERSION_CODE
        }

        initializers.init(this)

        try {
            System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to enable coroutine debugging")
        }

        updateAppWidgets(WidgetManager.UpdateReason.Unknown)
    }

    override fun onLowMemory() {
        Timber.v("onLowMemory()")
        super.onLowMemory()
    }


    // HasAndroidInjector Implementation

    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }


    // ActivityIntentProvider Implementation

    override fun provideMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java)
    }

    override fun provideAppWidgetIntents(): List<Intent> {
        return listOf(
            Intent(this, WidgetProvider41::class.java),
            Intent(this, WidgetProvider42::class.java)
        )
    }

    override fun updateAppWidgets(updateReason: WidgetManager.UpdateReason) {
        provideAppWidgetIntents().forEach { intent ->
            sendBroadcast(intent.apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(this@ShuttleApplication).getAppWidgetIds(component)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                putExtra(WidgetManager.ARG_UPDATE_REASON, updateReason.ordinal)
            })
        }
    }

    // OkHttpClientProvider Implementation

    override fun provideOkHttpClient(): OkHttpClient {
        return appComponent.okHttpClient()
    }


    // GeneralPreferenceManagerProvider Implementation

    override fun provideGeneralPreferenceManager(): GeneralPreferenceManager {
        return appComponent.generalPreferenceManager()
    }


    // SongRepositoryProvider Implementation

    override fun provideSongRepository(): SongRepository {
        return appComponent.songRepository()
    }
}