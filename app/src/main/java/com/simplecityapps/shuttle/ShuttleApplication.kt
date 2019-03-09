package com.simplecityapps.shuttle

import android.app.Activity
import android.app.Application
import android.app.Service
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.dagger.*
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import dagger.android.HasServiceInjector
import javax.inject.Inject

class ShuttleApplication : Application(), HasActivityInjector, HasServiceInjector, CoreComponentProvider {

    @Inject lateinit var dispatchingAndroidActivityInjector: DispatchingAndroidInjector<Activity>

    @Inject lateinit var dispatchingAndroidServiceInjector: DispatchingAndroidInjector<Service>

    @Inject lateinit var initializers: AppInitializers

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

        initializers.init(this)
    }


    // HasActivityInjector Implementation

    override fun activityInjector(): AndroidInjector<Activity> {
        return dispatchingAndroidActivityInjector
    }


    // HasServiceInjector Implementation

    override fun serviceInjector(): AndroidInjector<Service> {
        return dispatchingAndroidServiceInjector
    }


    // CoreComponent.Provider Implementation

    override fun provideCoreComponent(): CoreComponent {
        return coreComponent
    }


    companion object {

        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        }
    }
}