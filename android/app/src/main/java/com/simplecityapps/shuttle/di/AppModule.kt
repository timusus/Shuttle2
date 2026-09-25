package com.simplecityapps.shuttle.di

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.LruCache
import com.simplecityapps.mediaprovider.PlaylistExporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.library.albumartists.ArtistListPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.albumartists.ArtistListPreferences
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.*
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [AppBindsModule::class])
class AppModule {
    @Singleton
    @Provides
    fun provideDebugLoggingTree(
        @ApplicationContext context: Context,
        debugSettings: DebugSettings
    ): DebugLoggingTree = DebugLoggingTree(context, debugSettings)

    @Singleton
    @Provides
    fun provideArtworkCache(): LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(
            key: String,
            value: Bitmap
        ): Int = value.allocationByteCount
    }

    @Singleton
    @Provides
    fun providePlaylistExporter(
        @ApplicationContext context: Context,
    ): PlaylistExporter = PlaylistExporter(context)

    @Provides
    fun provideSortPreferences(preference: SharedPreferences): SortPreferences = SortPreferenceManager(preference)

    @Singleton
    @Provides
    fun provideArtistListPreferences(preferenceManager: GeneralPreferenceManager): ArtistListPreferences = ArtistListPreferenceManager(preferenceManager)

    @Singleton
    @Provides
    fun provideAlbumListPreferences(preferenceManager: GeneralPreferenceManager): AlbumListPreferences = AlbumListPreferenceManager(preferenceManager)

    @Singleton
    @Provides
    fun provideThemeManager(appearanceSettings: AppearanceSettings): ThemeManager = ThemeManager(appearanceSettings)

    @Singleton
    @Provides
    @Named("randomSeed")
    fun provideRandomSeed(): Long = Random().nextLong()

    @Provides
    fun provideRandom(): kotlin.random.Random = kotlin.random.Random.Default

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideAddToPlaylist(
        playlistRepository: PlaylistRepository,
        resolveSongs: ResolveSongs,
    ): AddToPlaylist = AddToPlaylist(playlistRepository, resolveSongs)
}
