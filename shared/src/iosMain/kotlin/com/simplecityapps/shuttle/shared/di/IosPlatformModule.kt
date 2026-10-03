package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.platform.PlatformFeatures
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.screens.sources.FolderKind
import com.simplecityapps.shuttle.ui.screens.sources.FolderLists
import com.simplecityapps.shuttle.ui.screens.sources.ScannerFolderStore
import com.simplecityapps.shuttle.ui.screens.sources.SourceFolder
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The platform seams iOS doesn't have yet (docs/architecture/ios-port/phase-4-platform-seams.md): no widgets, Cast,
 * downloads or local files, so each is off or a no-op, and the screens hide what they would offer.
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

    /** Every song is a server's, which the app can't delete. */
    @Provides
    fun provideSongFileDeleter(): SongFileDeleter = SongFileDeleter { false }

    /** iOS records no monetisation analytics, so a sign-in isn't recorded. */
    @Provides
    fun provideServerSignInAnalytics(): ServerSignInAnalytics = ServerSignInAnalytics { }

    /** iOS has no local-file scanner, so there are no scanner folders and none can be picked. */
    @Provides
    fun provideScannerFolderStore(): ScannerFolderStore = object : ScannerFolderStore {
        override val folders: StateFlow<FolderLists> = MutableStateFlow(FolderLists()).asStateFlow()

        override fun add(kind: FolderKind, treeUri: String): Boolean = false

        override fun remove(kind: FolderKind, folder: SourceFolder) = Unit

        override fun refresh() = Unit
    }

    /** No playlist files are imported on iOS, so none needs rewriting. */
    @Provides
    fun providePlaylistFileSync(): PlaylistFileSync = PlaylistFileSync.None
}
