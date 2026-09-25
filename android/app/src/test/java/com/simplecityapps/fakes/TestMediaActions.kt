package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.DownloadSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.FindGoToTarget
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.RemoveFromPlaylist
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.ShareSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import kotlinx.coroutines.Dispatchers

/** The shared media action use cases, wired to a test's fakes. */
class TestMediaActions(
    songRepository: SongRepository = FakeSongRepository(),
    genreRepository: GenreRepository = FakeGenreRepository(),
    playlistRepository: PlaylistRepository = FakePlaylistRepository(),
    queueManager: QueueOperations = FakeQueueManager(),
    playbackManager: PlaybackOperations = FakePlaybackManager(),
    albumRepository: AlbumRepository = FakeAlbumRepository(),
    albumArtistRepository: AlbumArtistRepository = FakeAlbumArtistRepository(),
) {
    /** Whether a song's file deletes; every delete succeeds by default. */
    var fileDeleter: SongFileDeleter = SongFileDeleter { true }

    /** The legacy `playlist_ignore_duplicates` setting. */
    var ignorePlaylistDuplicates: Boolean = false

    val resolveSongs = ResolveSongs(songRepository, genreRepository, playlistRepository, queueManager, ResolveFolderSongs(songRepository))
    val playSongs = PlaySongs(queueManager, playbackManager)
    val shuffleSongs = ShuffleSongs(playbackManager)
    val enqueueSongs = EnqueueSongs(playbackManager, resolveSongs)
    val addToPlaylist = AddToPlaylist(playlistRepository, resolveSongs) { ignorePlaylistDuplicates }
    val createPlaylist = CreatePlaylist(playlistRepository, resolveSongs)
    val excludeSongs = ExcludeSongs(songRepository, queueManager, resolveSongs)
    val deleteSongs = DeleteSongs(songRepository, queueManager, resolveSongs, { fileDeleter.delete(it) }, Dispatchers.Unconfined)
    val songDownloadManager = FakeSongDownloadManager()
    val mediaInfoProvider = FakeMediaInfoProvider()
    val downloadSongs = DownloadSongs(songDownloadManager, AggregateMediaInfoProvider(mutableSetOf(mediaInfoProvider)), resolveSongs)
    val findGoToTarget = FindGoToTarget(albumRepository, albumArtistRepository)
    val shareSongs = ShareSongs(resolveSongs)
    val removeFromPlaylist = RemoveFromPlaylist(playlistRepository)
    val handler = MediaActionHandler(
        resolveSongs = resolveSongs,
        playSongs = playSongs,
        shuffleSongs = shuffleSongs,
        enqueueSongs = enqueueSongs,
        addToPlaylist = addToPlaylist,
        createPlaylist = createPlaylist,
        findGoToTarget = findGoToTarget,
        shareSongs = shareSongs,
        excludeSongs = excludeSongs,
        deleteSongs = deleteSongs,
        downloadSongs = downloadSongs,
        removeFromPlaylist = removeFromPlaylist,
    )
}
