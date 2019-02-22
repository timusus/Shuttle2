package com.simplecityapps.shuttle

import android.app.Activity
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.stetho.Stetho
import com.simplecityapps.shuttle.core.dagger.RepositoryModule
import com.simplecityapps.shuttle.dagger.AppInjector
import com.simplecityapps.shuttle.dagger.CoreComponent
import com.simplecityapps.shuttle.dagger.CoreComponentProvider
import com.simplecityapps.shuttle.dagger.DaggerCoreComponent
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasActivityInjector
import timber.log.Timber
import javax.inject.Inject

class ShuttleApp : Application(), HasActivityInjector, CoreComponentProvider {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Activity>

    private val coreComponent: CoreComponent by lazy {
        DaggerCoreComponent
            .builder()
            .repositoryModule(RepositoryModule(this))
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        AppInjector.init(this)

        Timber.plant(Timber.DebugTree())

        Stetho.initializeWithDefaults(this)
    }

    override fun activityInjector(): AndroidInjector<Activity> {
        return dispatchingAndroidInjector
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