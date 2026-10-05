package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.TryDownloadFromServer
import com.simplecityapps.shuttle.entitlement.TryUseProFeature
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.ClearPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeletePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.DownloadSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.FavouriteSongs
import com.simplecityapps.shuttle.ui.actions.FindGoToTarget
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
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
import com.simplecityapps.shuttle.ui.actions.ResumeContext
import com.simplecityapps.shuttle.ui.actions.ShareSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.actions.UpdatePlaylistSortOrder
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs

/** The shared media action and library use cases, wired to a test's fakes. */
class TestMediaActions(
    songRepository: SongRepository = FakeSongRepository(),
    genreRepository: GenreRepository = FakeGenreRepository(),
    playlistRepository: PlaylistRepository = FakePlaylistRepository(),
    queueOperations: QueueOperations = FakeQueueOperations(),
    playbackOperations: PlaybackOperations = FakePlaybackOperations(),
    albumRepository: AlbumRepository = FakeAlbumRepository(),
    albumArtistRepository: AlbumArtistRepository = FakeAlbumArtistRepository(),
    playHistoryRepository: PlayHistoryRepository = FakePlayHistoryRepository(),
) {
    /** Whether a song's file deletes; every delete succeeds by default. */
    var fileDeleter: SongFileDeleter = SongFileDeleter { true }

    val resolveSongs = ResolveSongs(songRepository, genreRepository, playlistRepository, queueOperations, ResolveFolderSongs(songRepository))
    val playSongs = PlaySongs(queueOperations, playbackOperations)
    val shuffleSongs = ShuffleSongs(playbackOperations)
    val resumeContext = ResumeContext(queueOperations, playbackOperations, playHistoryRepository, resolveSongs)
    val enqueueSongs = EnqueueSongs(playbackOperations, resolveSongs)
    val addToPlaylist = AddToPlaylist(playlistRepository, resolveSongs)
    val createPlaylist = CreatePlaylist(playlistRepository, resolveSongs)
    val excludeSongs = ExcludeSongs(songRepository, queueOperations, resolveSongs)
    val deleteSongs = DeleteSongs(songRepository, queueOperations, resolveSongs, { fileDeleter.delete(it) })
    val songDownloader = FakeSongDownloader()

    /** Whether the user may download from a server (the entitlement gate); allowed by default. */
    var downloadAllowed = true
    val downloadSongs = DownloadSongs(songDownloader, resolveSongs, TryDownloadFromServer { downloadAllowed })

    /** Whether the user may use a Pro feature (the entitlement gate); allowed by default. Records each feature asked for. */
    var proFeatureAllowed = true
    val proFeaturesAsked = mutableListOf<ProFeature>()
    val tryUseProFeature = TryUseProFeature { feature ->
        proFeaturesAsked += feature
        proFeatureAllowed
    }
    val findGoToTarget = FindGoToTarget(albumRepository, albumArtistRepository)
    val shareSongs = ShareSongs(resolveSongs)
    val removeFromPlaylist = RemoveFromPlaylist(playlistRepository)
    val restorePlaylistSongs = RestorePlaylistSongs(playlistRepository)
    val renamePlaylist = RenamePlaylist(playlistRepository)
    val clearPlaylist = ClearPlaylist(playlistRepository)
    val deletePlaylist = DeletePlaylist(playlistRepository)
    val favouriteSongs = FavouriteSongs(songRepository, resolveSongs)
    val observeSongs = ObserveSongs(songRepository)
    val observeAlbums = ObserveAlbums(albumRepository)
    val observeArtists = ObserveArtists(albumArtistRepository)
    val observeArtistAlbums = ObserveArtistAlbums(albumRepository, songRepository)
    val observeGenres = ObserveGenres(genreRepository)
    val observeSongsForGenre = ObserveSongsForGenre(genreRepository)
    val observePlaylists = ObservePlaylists(playlistRepository)
    val observePlaylistSongs = ObservePlaylistSongs(playlistRepository)
    val updatePlaylistSortOrder = UpdatePlaylistSortOrder(playlistRepository)
    val reorderPlaylistSongs = ReorderPlaylistSongs(playlistRepository)
    val handler = MediaActionHandler(
        resolveSongs = resolveSongs,
        playSongs = playSongs,
        shuffleSongs = shuffleSongs,
        resumeContext = resumeContext,
        enqueueSongs = enqueueSongs,
        addToPlaylist = addToPlaylist,
        favouriteSongs = favouriteSongs,
        createPlaylist = createPlaylist,
        findGoToTarget = findGoToTarget,
        shareSongs = shareSongs,
        excludeSongs = excludeSongs,
        deleteSongs = deleteSongs,
        downloadSongs = downloadSongs,
        removeFromPlaylist = removeFromPlaylist,
        restorePlaylistSongs = restorePlaylistSongs,
        tryUseProFeature = tryUseProFeature,
    )
}
