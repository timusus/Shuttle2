package com.simplecityapps.shuttle.dagger

import com.simplecityapps.shuttle.ui.screens.debug.DebugDrawerFragment
import com.simplecityapps.shuttle.ui.screens.debug.LoggingFragment
import com.simplecityapps.shuttle.ui.screens.home.HomeFragment
import com.simplecityapps.shuttle.ui.screens.home.history.HistoryFragment
import com.simplecityapps.shuttle.ui.screens.home.recent.RecentFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListFragment
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.shuttle.ui.screens.settings.BottomDrawerSettingsFragment
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
    abstract fun contributeSongsFragment(): SongListFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumsFragment(): AlbumListFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumDetailFragment(): AlbumDetailFragment

    @ContributesAndroidInjector
    abstract fun contributePlaylistsFragment(): PlaylistListFragment

    @ContributesAndroidInjector
    abstract fun contributePlaylistDetailFragment(): PlaylistDetailFragment

    @ContributesAndroidInjector
    abstract fun contributeAlbumArtistsFragment(): AlbumArtistListFragment

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

    @ContributesAndroidInjector
    abstract fun contributeBottomSettingsFragment(): BottomDrawerSettingsFragment

}