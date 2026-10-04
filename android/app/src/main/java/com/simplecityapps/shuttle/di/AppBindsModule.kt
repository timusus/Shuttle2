package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.shuttle.appinitializers.AppInitializer
import com.simplecityapps.shuttle.appinitializers.AppearanceInitializer
import com.simplecityapps.shuttle.appinitializers.DownloadsInitializer
import com.simplecityapps.shuttle.appinitializers.EntitlementInitializer
import com.simplecityapps.shuttle.appinitializers.FavouriteSyncInitializer
import com.simplecityapps.shuttle.appinitializers.MediaProviderInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackInitializer
import com.simplecityapps.shuttle.appinitializers.PlaybackReportingInitializer
import com.simplecityapps.shuttle.appinitializers.SearchIndexInitializer
import com.simplecityapps.shuttle.appinitializers.ShortcutInitializer
import com.simplecityapps.shuttle.appinitializers.TelemetryInitializer
import com.simplecityapps.shuttle.appinitializers.TimberInitializer
import com.simplecityapps.shuttle.appinitializers.WidgetInitializer
import com.simplecityapps.shuttle.backup.LibraryBackupManager
import com.simplecityapps.shuttle.entitlement.EntitledServerStreamPolicy
import com.simplecityapps.shuttle.sources.DefaultMediaSources
import com.simplecityapps.shuttle.sources.SafScannerFolderStore
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.ScannerFolderStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet

@ContributesTo(AppScope::class)
@BindingContainer
abstract class AppBindsModule {
    @Binds
    abstract fun bindLibraryBackupFlow(impl: LibraryBackupManager): LibraryBackupFlow

    @Binds
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

    @Binds
    @IntoSet
    abstract fun provideSearchIndexInitializer(bind: SearchIndexInitializer): AppInitializer

    @Binds
    @IntoSet
    abstract fun provideFavouriteSyncInitializer(bind: FavouriteSyncInitializer): AppInitializer
}
