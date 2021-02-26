package com.simplecityapps.shuttle.dagger

import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.debug.DebugDrawerFragment
import com.simplecityapps.shuttle.ui.screens.debug.LoggingFragment
import com.simplecityapps.shuttle.ui.screens.equalizer.DspFragment
import com.simplecityapps.shuttle.ui.screens.equalizer.FrequencyResponseDialogFragment
import com.simplecityapps.shuttle.ui.screens.home.HomeFragment
import com.simplecityapps.shuttle.ui.screens.home.search.SearchFragment
import com.simplecityapps.shuttle.ui.screens.library.LibraryFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListFragment
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListFragment
import com.simplecityapps.shuttle.ui.screens.library.genres.detail.GenreDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListFragment
import com.simplecityapps.shuttle.ui.screens.main.MainFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.emby.EmbyConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.jellyfin.JellyfinConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.MediaProviderSelectionFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.scanner.MediaScannerFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.taglib.DirectorySelectionFragment
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.shuttle.ui.screens.settings.BottomDrawerSettingsFragment
import com.simplecityapps.shuttle.ui.screens.settings.SettingsFragment
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Suppress("unused")
@Module
abstract class FragmentBuildersModule {

    @ContributesAndroidInjector
    abstract fun contributeMainFragment(): MainFragment

    @ContributesAndroidInjector
    abstract fun contributeLibraryFragment(): LibraryFragment

    @ContributesAndroidInjector
    abstract fun contributeOnboardingFragment(): OnboardingParentFragment

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
    abstract fun contributeGenresFragment(): GenreListFragment

    @ContributesAndroidInjector
    abstract fun contributeGenreDetailFragment(): GenreDetailFragment

    @ContributesAndroidInjector
    abstract fun contributeSmartPlaylistDetailFragment(): SmartPlaylistDetailFragment

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
    abstract fun contributeHomeFragment(): HomeFragment

    @ContributesAndroidInjector
    abstract fun contributeBottomSettingsFragment(): BottomDrawerSettingsFragment

    @ContributesAndroidInjector
    abstract fun contributeSearchFragment(): SearchFragment

    @ContributesAndroidInjector
    abstract fun contributeMusicDirectoryFragment(): DirectorySelectionFragment

    @ContributesAndroidInjector
    abstract fun contributeScannerFragment(): MediaScannerFragment

    @ContributesAndroidInjector
    abstract fun contributeMediaProviderSelectionFragment(): MediaProviderSelectionFragment

    @ContributesAndroidInjector
    abstract fun contributeEqualizerFragment(): DspFragment

    @ContributesAndroidInjector
    abstract fun contributeChangelogDialog(): ChangelogDialogFragment

    @ContributesAndroidInjector
    abstract fun contributeSettingsFragment(): SettingsFragment

    @ContributesAndroidInjector
    abstract fun contributeFrequencyResponseFragment(): FrequencyResponseDialogFragment

    @ContributesAndroidInjector
    abstract fun contributeSongInfoDialog(): SongInfoDialogFragment

    @ContributesAndroidInjector
    abstract fun contributeTagEditorDialog(): TagEditorAlertDialog

    @ContributesAndroidInjector
    abstract fun contributeEmbyConfigurationFragment(): EmbyConfigurationFragment

    @ContributesAndroidInjector
    abstract fun contributeJellyfinConfigurationFragment(): JellyfinConfigurationFragment
}