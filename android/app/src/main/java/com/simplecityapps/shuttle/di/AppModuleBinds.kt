package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.appinitializers.AppInitializer
import com.simplecityapps.shuttle.appinitializers.CrashReportingInitializer
import com.simplecityapps.shuttle.appinitializers.DownloadsInitializer
import com.simplecityapps.shuttle.appinitializers.EntitlementInitializer
import com.simplecityapps.shuttle.appinitializers.MediaProviderInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackReportingInitializer
import com.simplecityapps.shuttle.appinitializers.RemoteConfigInitializer
import com.simplecityapps.shuttle.appinitializers.ShortcutInitializer
import com.simplecityapps.shuttle.appinitializers.TimberInitializer
import com.simplecityapps.shuttle.appinitializers.WidgetInitializer
import com.simplecityapps.shuttle.entitlement.EntitledServerStreamPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@InstallIn(SingletonComponent::class)
@Module
abstract class AppModuleBinds {
    @Binds
    abstract fun bindSongImportStateProvider(impl: MediaImportObserver): SongImportStateProvider

    @Binds
    abstract fun bindServerStreamPolicy(impl: EntitledServerStreamPolicy): ServerStreamPolicy

    @Binds
    @IntoSet
    abstract fun provideCrashReportingInitializer(bind: CrashReportingInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideTimberInitializer(bind: TimberInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun providePlaybackInitializer(bind: PlaybackInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun providePlaybackReportingInitializer(bind: PlaybackReportingInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideWidgetInitializer(bind: WidgetInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideMediaProviderInitializer(bind: MediaProviderInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideEntitlementInitializer(bind: EntitlementInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideRemoteConfigInitializer(bind: RemoteConfigInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideShortcutInitializer(bind: ShortcutInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideDownloadsInitializer(bind: DownloadsInitializer): AppInitializer
}
