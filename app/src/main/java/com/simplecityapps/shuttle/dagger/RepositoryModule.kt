package com.simplecityapps.shuttle.dagger

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStorePlaylistImporter
import com.simplecityapps.localmediaprovider.local.repository.*
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.*
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named

@Module
class RepositoryModule {

    @Provides
    @AppScope
    fun provideMediaDatabase(context: Context, @Named("AppCoroutineScope") appCoroutineScope: CoroutineScope): MediaDatabase {
        return DatabaseProvider(context, appCoroutineScope).database
    }

    @Provides
    @AppScope
    fun provideSongRepository(database: MediaDatabase): SongRepository {
        return LocalSongRepository(database.songDataDao())
    }

    @Provides
    @AppScope
    fun provideMediaImporter(songRepository: SongRepository): MediaImporter {
        return MediaImporter(songRepository)
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
    fun provideGenreRepository(songRepository: SongRepository): GenreRepository {
        return LocalGenreRepository(songRepository)
    }

    @Provides
    @AppScope
    fun providePlaylistImporter(context: Context, songRepository: SongRepository, playlistRepository: PlaylistRepository): MediaStorePlaylistImporter {
        return MediaStorePlaylistImporter(context, songRepository, playlistRepository)
    }
}