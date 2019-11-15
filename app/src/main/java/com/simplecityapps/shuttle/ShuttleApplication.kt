package com.simplecityapps.shuttle

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.dagger.*
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.widgets.ShuttleAppWidgetProvider
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import timber.log.Timber
import javax.inject.Inject

class ShuttleApplication : Application(),
    HasAndroidInjector,
    CoreComponentProvider,
    ActivityIntentProvider {

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject lateinit var initializers: AppInitializers

    @Inject lateinit var sharedPrefs: SharedPreferences

    private val coreComponent: CoreComponent by lazy {

        val persistenceModule = PersistenceModule(this)
        val repositoryModule = RepositoryModule(this)
        val playbackModule = PlaybackModule(this, persistenceModule.provideSharedPrefs())

        DaggerCoreComponent
            .builder()
            .repositoryModule(repositoryModule)
            .playbackModule(playbackModule)
            .persistenceModule(persistenceModule)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        AppInjector.init(this)

        when (sharedPrefs.getString("pref_night_mode", "0")) {
            "0" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        initializers.init(this)

        sendBroadcast(provideAppWidgetIntent().apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(this@ShuttleApplication).getAppWidgetIds(component)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        })
    }

    override fun onLowMemory() {
        Timber.v("onLowMemory()")
        super.onLowMemory()
    }


    // HasAndroidInjector Implementation

    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }


    // CoreComponentProvider Implementation

    override fun provideCoreComponent(): CoreComponent {
        return coreComponent
    }


    // ActivityIntentProvider Implementation

    override fun provideMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java)
    }

    override fun provideAppWidgetIntent(): Intent {
        return Intent(this, ShuttleAppWidgetProvider::class.java)
    }
}