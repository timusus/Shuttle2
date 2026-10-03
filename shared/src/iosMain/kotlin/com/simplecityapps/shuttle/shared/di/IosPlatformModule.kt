package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.platform.PlatformFeatures
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInAnalytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The platform seams iOS doesn't have yet (docs/architecture/ios-port/phase-4-platform-seams.md): no widgets, Cast
 * or downloads, so each is off or a no-op, and the screens hide what they would offer. Entitlements are
 * [IosEntitlementModule]'s and this device's files are `local/`'s.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class IosPlatformModule {
    @Provides
    fun providePlatformFeatures(): PlatformFeatures = PlatformFeatures(
        homeScreenWidgets = false,
        artworkPrefetch = false,
        scheduledRescan = false,
        cast = false,
        offlineDownloads = false,
    )

    @Provides
    fun provideRandom(): Random = Random.Default

    /** Home's shuffle seed: one per process, as on Android. */
    @SingleIn(AppScope::class)
    @Provides
    @Named("randomSeed")
    fun provideRandomSeed(): Long = Random.nextLong()

    /** Offline downloads are off ([PlatformFeatures.offlineDownloads]), so nothing is ever held. */
    @Provides
    fun provideSongDownloader(): SongDownloader = object : SongDownloader {
        override suspend fun download(song: Song): Boolean = false

        override fun remove(song: Song) = Unit

        override fun observeHeldPaths(): Flow<Set<String>> = flowOf(emptySet())
    }

    /** A server's songs can't be deleted, and iOS doesn't offer deleting this device's files (Files does that). */
    @Provides
    fun provideSongFileDeleter(): SongFileDeleter = SongFileDeleter { false }

    /** iOS records no monetisation analytics, so a sign-in isn't recorded. */
    @Provides
    fun provideServerSignInAnalytics(): ServerSignInAnalytics = ServerSignInAnalytics { }

    /** No playlist files are imported on iOS, so none needs rewriting. */
    @Provides
    fun providePlaylistFileSync(): PlaylistFileSync = PlaylistFileSync.None
}
