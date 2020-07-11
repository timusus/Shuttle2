package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStorePlaylistImporter
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

@Module
class RepositoryModule {

    @Provides
    @AppScope
    fun provideMediaDatabase(context: Context): MediaDatabase {
        return DatabaseProvider(context).database
    }

    @Provides
    @AppScope
    fun provideSongRepository(database: MediaDatabase): SongRepository {
        return LocalSongRepository(database.songDataDao())
    }

    @Provides
    @AppScope
    fun provideMediaImporter(context: Context, songRepository: SongRepository): MediaImporter {
        return MediaImporter(context, songRepository, CoroutineScope(Dispatchers.Main + CoroutineExceptionHandler { _, exception -> Timber.e(exception) }))
    }

    @Provides
    @AppScope
    fun provideAlbumRepository(database: MediaDatabase): AlbumRepository {
        return LocalAlbumRepository(database.albumDataDao())
    }

    @Provides
    @AppScope
    fun provideAlbumArtistRepository(database: MediaDatabase): AlbumArtistRepository {
        return LocalAlbumArtistRepository(database.albumArtistDataDao())
    }

    @Provides
    @AppScope
    fun providePlaylistRepository(database: MediaDatabase): PlaylistRepository {
        return LocalPlaylistRepository(database.playlistDataDao(), database.playlistSongJoinDataDao())
    }

    @Provides
    @AppScope
    fun providePlaylistImporter(context: Context, songRepository: SongRepository, playlistRepository: PlaylistRepository): MediaStorePlaylistImporter {
        return MediaStorePlaylistImporter(context, songRepository, playlistRepository)
    }
}