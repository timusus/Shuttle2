package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.platform.PlatformFeatures
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Android's side of the shared presentation layer's platform seams (docs/architecture/ios-port/phase-4-platform-seams.md). */
@ContributesTo(AppScope::class)
@BindingContainer
object PlatformModule {
    @Provides
    fun provideAppVersion(): AppVersion = AppVersion { BuildConfig.VERSION_NAME }

    /** Android does everything the shared lists offer. */
    @Provides
    fun providePlatformFeatures(): PlatformFeatures = PlatformFeatures(
        homeScreenWidgets = true,
        artworkPrefetch = true,
        scheduledRescan = true,
        cast = true,
        offlineDownloads = true,
    )
}
