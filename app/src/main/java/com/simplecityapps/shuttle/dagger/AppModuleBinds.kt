package com.simplecityapps.shuttle.dagger

import android.app.Application
import com.simplecityapps.shuttle.ShuttleApplication
import com.simplecityapps.shuttle.appinitializers.*
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

@Module
abstract class AppModuleBinds {

    @Binds
    abstract fun provideApplication(bind: ShuttleApplication): Application

    @Binds
    @IntoSet
    abstract fun provideBugsnagInitializer(bind: BugsnagInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideTimberInitializer(bind: TimberInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun providePlaybackInitializer(bind: PlaybackInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideWidgetInitializer(bind: WidgetInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideMediaProviderInitializer(bind: MediaProviderInitializer): AppInitializer
}