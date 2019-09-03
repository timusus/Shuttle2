package com.simplecityapps.shuttle

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.dagger.PlaybackModule
import com.simplecityapps.shuttle.appinitializers.AppInitializers
import com.simplecityapps.shuttle.dagger.*
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ShuttleApplication : Application(),
    HasAndroidInjector,
    CoreComponentProvider,
    ActivityIntentProvider {

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

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


    // HasAndroidInjector Implementation

    override fun androidInjector(): AndroidInjector<Any> {
        return dispatchingAndroidInjector
    }


    // CoreComponentProvider Implementation

    override fun provideCoreComponent(): CoreComponent {
        return coreComponent
    }


    // MainActivityIntentProvider Implementation

    override fun provideMainActivityIntent(): Intent {
        return Intent(this, MainActivity::class.java)
    }


    companion object {

        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}