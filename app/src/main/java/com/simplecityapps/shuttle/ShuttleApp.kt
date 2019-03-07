package com.simplecityapps.shuttle

import android.app.Activity
import android.app.Application
import android.app.Service
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.stetho.Stetho
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.shuttle.dagger.*
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasServiceInjector
import timber.log.Timber
import javax.inject.Inject

class ShuttleApp : Application(), HasActivityInjector, HasServiceInjector, CoreComponentProvider {

    @Inject
    lateinit var dispatchingAndroidActivityInjector: DispatchingAndroidInjector<Activity>

    @Inject
    lateinit var dispatchingAndroidServiceInjector: DispatchingAndroidInjector<Service>

    private val coreComponent: CoreComponent by lazy {
        DaggerCoreComponent
            .builder()
            .repositoryModule(RepositoryModule(this))
            .playbackModule(PlaybackModule(this))
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        AppInjector.init(this)

        Timber.plant(Timber.DebugTree())

        Stetho.initializeWithDefaults(this)
    }

    override fun activityInjector(): AndroidInjector<Activity> {
        return dispatchingAndroidActivityInjector
    }

    override fun serviceInjector(): AndroidInjector<Service> {
        return dispatchingAndroidServiceInjector
    }

    // CoreComponent.Provider Implementation

    override fun provideCoreComponent(): CoreComponent {
        return coreComponent
    }

    companion object {

        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}