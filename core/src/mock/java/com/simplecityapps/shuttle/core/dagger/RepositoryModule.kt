package com.simplecityapps.shuttle.core.dagger

import android.content.Context
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
open class RepositoryModule(val context: Context) {

    @Singleton
    @Provides
    fun provideSongRepository(): SongRepository {
        return MockSongRepository(context)
    }

    @Singleton
    @Provides
    fun provideAlbumRepository(): AlbumRepository {
        return MockAlbumRepository(context)
    }

    @Singleton
    @Provides
    fun provideAlbumArtistRepository(): AlbumArtistRepository {
        return MockAlbumArtistRepository(context)
    }

}