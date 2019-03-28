package com.simplecityapps.shuttle.dagger

import com.simplecityapps.shuttle.ui.screens.debug.DebugDrawerFragment
import com.simplecityapps.shuttle.ui.screens.debug.LoggingFragment
import com.simplecityapps.shuttle.ui.screens.home.HomeFragment
import com.simplecityapps.shuttle.ui.screens.home.history.HistoryFragment
import com.simplecityapps.shuttle.ui.screens.home.recent.RecentFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistsFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumsFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongsFragment
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Suppress("unused")
@Module
abstract class FragmentBuildersModule {

    @ContributesAndroidInjector
    abstract fun contributeFolderFragment(): FolderFragment

    @ContributesAndroidInjector
    abstract fun contributeFolderDetailFragment(): FolderDetailFragment

    @ContributesAndroidInjector
    abstract fun contributeSongsFragment(): SongsFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumsFragment(): AlbumsFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumDetailFragment(): AlbumDetailFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumArtistsFragment(): AlbumArtistsFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumArtistDetailFragment(): AlbumArtistDetailFragment

    @ContributesAndroidInjector
    abstract fun contributeQueueFragment(): QueueFragment

    @ContributesAndroidInjector
    abstract fun contributePlaybackFragment(): PlaybackFragment

    @ContributesAndroidInjector
    abstract fun contributeMiniPlaybackFragment(): MiniPlaybackFragment

    @ContributesAndroidInjector
    abstract fun contributeDebugDrawerFragment(): DebugDrawerFragment

    @ContributesAndroidInjector
    abstract fun contributeLoggingFragment(): LoggingFragment

    @ContributesAndroidInjector
    abstract fun contributeSleepTimerDialogFragment(): SleepTimerDialogFragment

    @ContributesAndroidInjector
    abstract fun contributeHistoryFragment(): HistoryFragment

    @ContributesAndroidInjector
    abstract fun contributeRecentFragment(): RecentFragment

    @ContributesAndroidInjector
    abstract fun contributeHomeFragment(): HomeFragment

}