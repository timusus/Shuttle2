package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.ClearPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeletePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.EvaluateSmartPlaylist
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.FavouriteSongs
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveFavouriteSongIds
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ObserveSongsForGenre
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.RemoveFromPlaylist
import com.simplecityapps.shuttle.ui.actions.RenamePlaylist
import com.simplecityapps.shuttle.ui.actions.ReorderPlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.RestorePlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ShareSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.actions.ToggleFavourite
import com.simplecityapps.shuttle.ui.actions.UpdatePlaylistSortOrder
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hands Hilt the :android:domain use cases. Domain is Kotlin Multiplatform (#582) and marks them with Metro's
 * `@Inject`, which Hilt can't see, so each is constructed here as its `@Inject` constructor would be: unscoped.
 * Removed in phase 1 (#583), when the app moves from Hilt to Metro.
 */
@InstallIn(SingletonComponent::class)
@Module
object DomainBridgeModule {
    @Provides
    fun provideAddToPlaylist(
        playlistRepository: PlaylistRepository,
        resolveSongs: ResolveSongs,
    ): AddToPlaylist = AddToPlaylist(playlistRepository, resolveSongs)

    @Provides
    fun provideClearPlaylist(playlistRepository: PlaylistRepository): ClearPlaylist = ClearPlaylist(playlistRepository)

    @Provides
    fun provideCreatePlaylist(
        playlistRepository: PlaylistRepository,
        resolveSongs: ResolveSongs,
    ): CreatePlaylist = CreatePlaylist(playlistRepository, resolveSongs)

    @Provides
    fun provideDeletePlaylist(playlistRepository: PlaylistRepository): DeletePlaylist = DeletePlaylist(playlistRepository)

    @Provides
    fun provideDeleteSongs(
        songRepository: SongRepository,
        queueOperations: QueueOperations,
        resolveSongs: ResolveSongs,
        fileDeleter: SongFileDeleter,
    ): DeleteSongs = DeleteSongs(songRepository, queueOperations, resolveSongs, fileDeleter)

    @Provides
    fun provideEnqueueSongs(
        playbackOperations: PlaybackOperations,
        resolveSongs: ResolveSongs,
    ): EnqueueSongs = EnqueueSongs(playbackOperations, resolveSongs)

    @Provides
    fun provideEvaluateSmartPlaylist(
        songRepository: SongRepository,
    ): EvaluateSmartPlaylist = EvaluateSmartPlaylist(songRepository)

    @Provides
    fun provideExcludeSongs(
        songRepository: SongRepository,
        queueOperations: QueueOperations,
        resolveSongs: ResolveSongs,
    ): ExcludeSongs = ExcludeSongs(songRepository, queueOperations, resolveSongs)

    @Provides
    fun provideFavouriteSongs(
        songRepository: SongRepository,
        resolveSongs: ResolveSongs,
    ): FavouriteSongs = FavouriteSongs(songRepository, resolveSongs)

    @Provides
    fun provideObserveAlbumArtists(
        albumArtistRepository: AlbumArtistRepository,
    ): ObserveAlbumArtists = ObserveAlbumArtists(albumArtistRepository)

    @Provides
    fun provideObserveAlbums(albumRepository: AlbumRepository): ObserveAlbums = ObserveAlbums(albumRepository)

    @Provides
    fun provideObserveCurrentSong(queueOperations: QueueOperations): ObserveCurrentSong = ObserveCurrentSong(queueOperations)

    @Provides
    fun provideObserveFavouriteSongIds(
        songRepository: SongRepository,
    ): ObserveFavouriteSongIds = ObserveFavouriteSongIds(songRepository)

    @Provides
    fun provideObserveGenres(genreRepository: GenreRepository): ObserveGenres = ObserveGenres(genreRepository)

    @Provides
    fun provideObservePlaylistCovers(
        playlistRepository: PlaylistRepository,
    ): ObservePlaylistCovers = ObservePlaylistCovers(playlistRepository)

    @Provides
    fun provideObservePlaylistSongs(
        playlistRepository: PlaylistRepository,
    ): ObservePlaylistSongs = ObservePlaylistSongs(playlistRepository)

    @Provides
    fun provideObservePlaylists(playlistRepository: PlaylistRepository): ObservePlaylists = ObservePlaylists(playlistRepository)

    @Provides
    fun provideObserveSongs(songRepository: SongRepository): ObserveSongs = ObserveSongs(songRepository)

    @Provides
    fun provideObserveSongsForGenre(
        genreRepository: GenreRepository,
    ): ObserveSongsForGenre = ObserveSongsForGenre(genreRepository)

    @Provides
    fun providePlaySongs(
        queueOperations: QueueOperations,
        playbackOperations: PlaybackOperations,
    ): PlaySongs = PlaySongs(queueOperations, playbackOperations)

    @Provides
    fun provideRemoveFromPlaylist(
        playlistRepository: PlaylistRepository,
    ): RemoveFromPlaylist = RemoveFromPlaylist(playlistRepository)

    @Provides
    fun provideRenamePlaylist(playlistRepository: PlaylistRepository): RenamePlaylist = RenamePlaylist(playlistRepository)

    @Provides
    fun provideReorderPlaylistSongs(
        playlistRepository: PlaylistRepository,
    ): ReorderPlaylistSongs = ReorderPlaylistSongs(playlistRepository)

    @Provides
    fun provideResolveFolderSongs(songRepository: SongRepository): ResolveFolderSongs = ResolveFolderSongs(songRepository)

    @Provides
    fun provideResolveSongs(
        songRepository: SongRepository,
        genreRepository: GenreRepository,
        playlistRepository: PlaylistRepository,
        queueOperations: QueueOperations,
        resolveFolderSongs: ResolveFolderSongs,
    ): ResolveSongs = ResolveSongs(songRepository, genreRepository, playlistRepository, queueOperations, resolveFolderSongs)

    @Provides
    fun provideRestorePlaylistSongs(
        playlistRepository: PlaylistRepository,
    ): RestorePlaylistSongs = RestorePlaylistSongs(playlistRepository)

    @Provides
    fun provideShareSongs(resolveSongs: ResolveSongs): ShareSongs = ShareSongs(resolveSongs)

    @Provides
    fun provideShuffleAlbums(
        queueOperations: QueueOperations,
        playbackOperations: PlaybackOperations,
    ): ShuffleAlbums = ShuffleAlbums(queueOperations, playbackOperations)

    @Provides
    fun provideShuffleSongs(playbackOperations: PlaybackOperations): ShuffleSongs = ShuffleSongs(playbackOperations)

    @Provides
    fun provideToggleFavourite(songRepository: SongRepository): ToggleFavourite = ToggleFavourite(songRepository)

    @Provides
    fun provideUpdatePlaylistSortOrder(
        playlistRepository: PlaylistRepository,
    ): UpdatePlaylistSortOrder = UpdatePlaylistSortOrder(playlistRepository)
}
