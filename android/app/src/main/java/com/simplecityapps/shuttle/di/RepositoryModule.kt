package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalGenreRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
class RepositoryModule {
    @Provides
    @Singleton
    fun provideMediaDatabase(
        @ApplicationContext context: Context
    ): MediaDatabase = DatabaseProvider(context).database

    @Provides
    @Singleton
    fun provideSongRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): SongRepository = LocalSongRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun provideMediaImporter(
        @ApplicationContext context: Context,
        songRepository: SongRepository,
        playlistRepository: PlaylistRepository,
        preferenceManager: GeneralPreferenceManager
    ): MediaImporter = MediaImporter(context, songRepository, playlistRepository, preferenceManager)

    @Provides
    @Singleton
    fun provideAlbumRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): AlbumRepository = LocalAlbumRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun provideAlbumArtistRepository(
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): AlbumArtistRepository = LocalAlbumArtistRepository(appCoroutineScope, database.songDataDao())

    @Provides
    @Singleton
    fun providePlaylistRepository(
        @ApplicationContext context: Context,
        database: MediaDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): PlaylistRepository = LocalPlaylistRepository(context, appCoroutineScope, database.playlistDataDao(), database.playlistSongJoinDataDao())

    @Provides
    @Singleton
    fun provideGenreRepository(
        songRepository: SongRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): GenreRepository = LocalGenreRepository(appCoroutineScope, songRepository)
}
