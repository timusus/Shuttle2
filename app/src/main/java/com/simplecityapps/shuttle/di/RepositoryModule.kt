package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStorePlaylistImporter
import com.simplecityapps.localmediaprovider.local.repository.*
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideMediaDatabase(@ApplicationContext context: Context, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): MediaDatabase {
        return DatabaseProvider(context).database
    }

    @Provides
    @Singleton
    fun provideSongRepository(database: MediaDatabase, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): SongRepository {
        return LocalSongRepository(appCoroutineScope, database.songDataDao())
    }

    @Provides
    @Singleton
    fun provideMediaImporter(songRepository: SongRepository): MediaImporter {
        return MediaImporter(songRepository)
    }

    @Provides
    @Singleton
    fun provideAlbumRepository(database: MediaDatabase, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): AlbumRepository {
        return LocalAlbumRepository(appCoroutineScope, database.songDataDao())
    }

    @Provides
    @Singleton
    fun provideAlbumArtistRepository(database: MediaDatabase, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): AlbumArtistRepository {
        return LocalAlbumArtistRepository(appCoroutineScope, database.songDataDao())
    }

    @Provides
    @Singleton
    fun providePlaylistRepository(@ApplicationContext context: Context, database: MediaDatabase, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): PlaylistRepository {
        return LocalPlaylistRepository(context, appCoroutineScope, database.playlistDataDao(), database.playlistSongJoinDataDao())
    }

    @Provides
    @Singleton
    fun provideGenreRepository(songRepository: SongRepository, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): GenreRepository {
        return LocalGenreRepository(appCoroutineScope, songRepository)
    }

    @Provides
    @Singleton
    fun providePlaylistImporter(@ApplicationContext context: Context, songRepository: SongRepository, playlistRepository: PlaylistRepository): MediaStorePlaylistImporter {
        return MediaStorePlaylistImporter(context, songRepository, playlistRepository)
    }
}