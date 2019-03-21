package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.taglib.FileScanner
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepositoryModule(private val context: Context, private val fileScanner: FileScanner) {

    @Provides
    @Singleton
    fun provideMediaDatabase(): MediaDatabase {
        return DatabaseProvider(context).database
    }

    @Provides
    @Singleton
    fun provideSongRepository(database: MediaDatabase): SongRepository {
        return LocalSongRepository(fileScanner, database)
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
}