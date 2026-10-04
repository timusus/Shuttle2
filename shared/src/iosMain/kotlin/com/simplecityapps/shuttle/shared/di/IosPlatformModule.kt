package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.shuttle.platform.PlatformFeatures
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.backup.RestoreReport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The platform seams iOS doesn't have yet (docs/architecture/ios-port/phase-4-platform-seams.md): no widgets or Cast,
 * so each is off or a no-op, and the screens hide what they would offer. Offline downloads are [IosDownloadsModule]'s,
 * entitlements [IosEntitlementModule]'s and this device's files `local/`'s.
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
        offlineDownloads = true,
    )

    @Provides
    fun provideRandom(): Random = Random.Default

    /** Home's shuffle seed: one per process, as on Android. */
    @SingleIn(AppScope::class)
    @Provides
    @Named("randomSeed")
    fun provideRandomSeed(): Long = Random.nextLong()

    /** A server's songs can't be deleted, and iOS doesn't offer deleting this device's files (Files does that). */
    @Provides
    fun provideSongFileDeleter(): SongFileDeleter = SongFileDeleter { false }

    /** No playlist files are imported on iOS, so none needs rewriting. */
    @Provides
    fun providePlaylistFileSync(): PlaylistFileSync = PlaylistFileSync.None

    /** iOS has no backup export or import yet, so the Library backup rows (which iOS doesn't list) report failure. */
    @Provides
    fun provideLibraryBackupFlow(): LibraryBackupFlow = object : LibraryBackupFlow {
        override suspend fun buildBackupJson(): String? = null

        override suspend fun writeBackup(destinationUri: String, backupJson: String): Boolean = false

        override suspend fun readAndRestore(sourceUri: String): RestoreReport? = null
    }
}
