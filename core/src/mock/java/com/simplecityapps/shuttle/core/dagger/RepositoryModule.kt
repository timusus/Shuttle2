package com.simplecityapps.shuttle.core.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.repository.MockAlbumArtistRepository
import com.simplecityapps.localmediaprovider.repository.MockAlbumRepository
import com.simplecityapps.localmediaprovider.repository.MockSongRepository
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideMediaDatabase(context: Context): MediaDatabase {
        return DatabaseProvider(context).database
    }

    @Provides
    @Singleton
    fun provideSongRepository(context: Context): SongRepository {
        return MockSongRepository(context)
    }

    @Provides
    @Singleton
    fun provideAlbumRepository(context: Context): AlbumRepository {
        return MockAlbumRepository(context)
    }

    @Provides
    @Singleton
    fun provideAlbumArtistRepository(context: Context): AlbumArtistRepository {
        return MockAlbumArtistRepository(context)
    }
}