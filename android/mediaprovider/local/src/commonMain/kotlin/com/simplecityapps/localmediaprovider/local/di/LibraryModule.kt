package com.simplecityapps.localmediaprovider.local.di

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalGenreRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSmartPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.mediaprovider.ImportedPlaylistStore
import com.simplecityapps.mediaprovider.MediaImportStrings
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.smartplaylists.SmartPlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

/**
 * The library both platforms share: the repositories over the [MediaDatabase] and the [MediaImporter] that fills it.
 * Each platform provides the database and its [PlaylistFileSync] (SAF on Android, none on iOS).
 */
@ContributesTo(AppScope::class)
@BindingContainer
abstract class LibraryModule {
    @Binds
    abstract fun bindSongImportStateProvider(impl: MediaImporter): SongImportStateProvider

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideSongRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): SongRepository = LocalSongRepository(appCoroutineScope, database.songDataDao())

        @Provides
        @SingleIn(AppScope::class)
        fun provideMediaImporter(
            strings: MediaImportStrings,
            songRepository: SongRepository,
            playlistStore: ImportedPlaylistStore,
            preferenceManager: GeneralPreferenceManager
        ): MediaImporter = MediaImporter(strings, songRepository, playlistStore, preferenceManager)

        @Provides
        @SingleIn(AppScope::class)
        fun provideAlbumRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): AlbumRepository = LocalAlbumRepository(appCoroutineScope, database.songDataDao())

        @Provides
        @SingleIn(AppScope::class)
        fun provideAlbumArtistRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): AlbumArtistRepository = LocalAlbumArtistRepository(appCoroutineScope, database.songDataDao())

        @Provides
        @SingleIn(AppScope::class)
        fun provideLocalPlaylistRepository(
            database: MediaDatabase,
            fileSync: PlaylistFileSync,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): LocalPlaylistRepository = LocalPlaylistRepository(appCoroutineScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), fileSync)

        @Provides
        fun providePlaylistRepository(playlistRepository: LocalPlaylistRepository): PlaylistRepository = playlistRepository

        @Provides
        fun provideImportedPlaylistStore(playlistRepository: LocalPlaylistRepository): ImportedPlaylistStore = playlistRepository

        @Provides
        @SingleIn(AppScope::class)
        fun provideSmartPlaylistRepository(database: MediaDatabase): SmartPlaylistRepository = LocalSmartPlaylistRepository(database.smartPlaylistDao())

        @Provides
        @SingleIn(AppScope::class)
        fun provideGenreRepository(
            songRepository: SongRepository,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): GenreRepository = LocalGenreRepository(appCoroutineScope, songRepository)
    }
}
