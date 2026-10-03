package com.simplecityapps.localmediaprovider.local.di

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.repository.AlbumKeyMigration
import com.simplecityapps.localmediaprovider.local.repository.LibraryAlbumIndex
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumArtistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalAlbumRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalGenreRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlayHistoryRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSmartPlaylistRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSongRepository
import com.simplecityapps.localmediaprovider.local.repository.LocalSuggestionsRepository
import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.localmediaprovider.local.repository.libraryAlbumIndex
import com.simplecityapps.mediaprovider.ImportedPlaylistStore
import com.simplecityapps.mediaprovider.MediaImportStrings
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.smartplaylists.SmartPlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * The library both platforms share: the repositories over the [MediaDatabase] and the [MediaImporter] that fills it.
 * Each platform provides the database and its [PlaylistFileSync] (SAF on Android, none on iOS).
 */
@ContributesTo(AppScope::class)
@BindingContainer
abstract class LibraryModule {
    @Binds
    abstract fun bindSongImportStateProvider(impl: MediaImporter): SongImportStateProvider

    @Binds
    abstract fun bindAlbumIndexProvider(impl: LibraryAlbumIndex): AlbumIndexProvider

    companion object {
        /**
         * The library's one album index, rebuilt only when the library's album identities change. Built in the
         * background as soon as it's made, at app start, so Home's first load doesn't wait for it.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideLibraryAlbumIndex(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope
        ): LibraryAlbumIndex = database.libraryAlbumIndex().also { index -> appCoroutineScope.launch(Dispatchers.IO) { index.albumIndex() } }

        @Provides
        @SingleIn(AppScope::class)
        fun provideSongRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            albumIndex: LibraryAlbumIndex,
            librarySettings: LibrarySettings
        ): SongRepository = LocalSongRepository(appCoroutineScope, database.songDataDao(), albumIndex, librarySettings.minTrackLength.flow)

        @Provides
        @SingleIn(AppScope::class)
        fun provideMediaImporter(
            strings: MediaImportStrings,
            songRepository: SongRepository,
            playlistStore: ImportedPlaylistStore,
            preferenceManager: GeneralPreferenceManager,
            database: MediaDatabase
        ): MediaImporter {
            val albumKeyMigration = AlbumKeyMigration(database.songDataDao(), database.playEventDao(), database.pinnedCollectionDao(), preferenceManager)
            return MediaImporter(strings, songRepository, playlistStore, preferenceManager, albumKeyMigration::migrateIfDue)
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideAlbumRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            librarySettings: LibrarySettings
        ): AlbumRepository = LocalAlbumRepository(appCoroutineScope, database.songDataDao(), librarySettings.minTrackLength.flow)

        @Provides
        @SingleIn(AppScope::class)
        fun provideAlbumArtistRepository(
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            librarySettings: LibrarySettings
        ): AlbumArtistRepository = LocalAlbumArtistRepository(appCoroutineScope, database.songDataDao(), librarySettings.minTrackLength.flow)

        @Provides
        @SingleIn(AppScope::class)
        fun provideLocalPlaylistRepository(
            database: MediaDatabase,
            fileSync: PlaylistFileSync,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            albumIndex: LibraryAlbumIndex
        ): LocalPlaylistRepository = LocalPlaylistRepository(appCoroutineScope, database.playlistDataDao(), database.playlistSongJoinDataDao(), fileSync, albumIndex)

        @Provides
        fun providePlaylistRepository(playlistRepository: LocalPlaylistRepository): PlaylistRepository = playlistRepository

        @Provides
        fun provideImportedPlaylistStore(playlistRepository: LocalPlaylistRepository): ImportedPlaylistStore = playlistRepository

        @Provides
        @SingleIn(AppScope::class)
        fun provideSmartPlaylistRepository(database: MediaDatabase): SmartPlaylistRepository = LocalSmartPlaylistRepository(database.smartPlaylistDao())

        @Provides
        @SingleIn(AppScope::class)
        fun providePlayHistoryRepository(
            database: MediaDatabase,
            albumIndex: LibraryAlbumIndex
        ): PlayHistoryRepository = LocalPlayHistoryRepository(database.playEventDao(), database.resumePointDao(), albumIndex)

        @Provides
        @SingleIn(AppScope::class)
        fun provideSuggestionsRepository(
            database: MediaDatabase,
            albumIndex: LibraryAlbumIndex
        ): SuggestionsRepository = LocalSuggestionsRepository(database.suggestionsDao(), albumIndex)

        @Provides
        @SingleIn(AppScope::class)
        fun provideGenreRepository(
            songRepository: SongRepository,
            database: MediaDatabase,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            albumIndex: LibraryAlbumIndex
        ): GenreRepository = LocalGenreRepository(appCoroutineScope, songRepository, database.songDataDao(), albumIndex)
    }
}
