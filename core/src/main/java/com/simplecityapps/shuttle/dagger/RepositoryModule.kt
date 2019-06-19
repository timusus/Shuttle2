package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.SongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.taglib.FileScanner
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepositoryModule(
    private val context: Context,
    private val fileScanner: FileScanner
) {

    @Provides
    @Singleton
    fun provideMediaDatabase(): MediaDatabase {
        return DatabaseProvider(context).database
    }

    @Provides
    @Singleton
    fun provideSongProvider(): SongProvider {
        return TaglibSongProvider(fileScanner)
    }

    @Provides
    @Singleton
    fun provideSongRepository(database: MediaDatabase, songProvider: SongProvider): SongRepository {
        return LocalSongRepository(database, songProvider)
    }

    @Provides
    @Singleton
    fun provideAlbumRepository(database: MediaDatabase): AlbumRepository {
        return LocalAlbumRepository(database)
    }

    @Provides
    @Singleton
    fun provideAlbumArtistRepository(database: MediaDatabase): AlbumArtistRepository {
        return LocalAlbumArtistRepository(database)
    }

    @Provides
    @Singleton
    fun providePlaylistRepository(database: MediaDatabase): PlaylistRepository {
        return LocalPlaylistRepository(database)
    }
}