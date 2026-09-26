package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.appinitializers.AppInitializer
import com.simplecityapps.shuttle.appinitializers.AppearanceInitializer
import com.simplecityapps.shuttle.appinitializers.DownloadsInitializer
import com.simplecityapps.shuttle.appinitializers.EntitlementInitializer
import com.simplecityapps.shuttle.appinitializers.MediaProviderInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackReportingInitializer
import com.simplecityapps.shuttle.appinitializers.ShortcutInitializer
import com.simplecityapps.shuttle.appinitializers.TelemetryInitializer
import com.simplecityapps.shuttle.appinitializers.TimberInitializer
import com.simplecityapps.shuttle.appinitializers.WidgetInitializer
import com.simplecityapps.shuttle.debug.livelog.LiveLogSink
import com.simplecityapps.shuttle.entitlement.EntitledServerStreamPolicy
import com.simplecityapps.shuttle.ui.screens.settings.LiveLogEntryPoint
import com.simplecityapps.shuttle.ui.screens.sources.DefaultMediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.SafScannerFolderStore
import com.simplecityapps.shuttle.ui.screens.sources.ScannerFolderStore
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class AppBindsModule {
    @Binds
    abstract fun bindSongImportStateProvider(impl: MediaImporter): SongImportStateProvider

    @Binds
    @Singleton
    abstract fun bindServerStreamPolicy(impl: EntitledServerStreamPolicy): ServerStreamPolicy

    @Binds
    abstract fun bindMediaSources(impl: DefaultMediaSources): MediaSources

    @Binds
    abstract fun bindScannerFolderStore(impl: SafScannerFolderStore): ScannerFolderStore

    @Binds
    @IntoSet
    abstract fun provideTelemetryInitializer(bind: TelemetryInitializer): AppInitializer

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
    abstract fun provideShortcutInitializer(bind: ShortcutInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideDownloadsInitializer(bind: DownloadsInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideAppearanceInitializer(bind: AppearanceInitializer): AppInitializer

    /** Absent in release: only the debug build (`android/app/src/debug`) binds these. */
    @BindsOptionalOf
    abstract fun bindLiveLogSink(): LiveLogSink

    @BindsOptionalOf
    abstract fun bindLiveLogEntryPoint(): LiveLogEntryPoint
}
