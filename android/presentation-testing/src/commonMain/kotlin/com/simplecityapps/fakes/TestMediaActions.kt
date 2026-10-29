package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.AggregateSimilarSongsProvider
import com.simplecityapps.mediaprovider.SimilarSongsProvider
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.entitlement.TryDownloadFromServer
import com.simplecityapps.shuttle.entitlement.TryUseProFeature
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.DownloadSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.FavouriteSongs
import com.simplecityapps.shuttle.ui.actions.FilterPlayableSongs
import com.simplecityapps.shuttle.ui.actions.FindGoToTarget
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaStoreSongDeleter
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.PlayAfterWork
import com.simplecityapps.shuttle.ui.actions.PlayFromCopy
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.RestorePlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ResumeContext
import com.simplecityapps.shuttle.ui.actions.ShareSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleWorks
import com.simplecityapps.shuttle.ui.actions.SongFileDeleter
import com.simplecityapps.shuttle.ui.actions.StartRadio
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow

/** The shared media action and library use cases, wired to a test's fakes. */
class TestMediaActions(
    songRepository: SongRepository = FakeSongRepository(),
    genreRepository: GenreRepository = FakeGenreRepository(),
    val playlistRepository: PlaylistRepository = FakePlaylistRepository(),
    queueOperations: QueueOperations = FakeQueueOperations(),
    playbackOperations: PlaybackOperations = FakePlaybackOperations(),
    albumRepository: AlbumRepository = FakeAlbumRepository(),
    albumArtistRepository: AlbumArtistRepository = FakeAlbumArtistRepository(),
    playHistoryRepository: PlayHistoryRepository = FakePlayHistoryRepository(),
    val offline: FakeOfflineSongs = FakeOfflineSongs(),
) {
    val filterPlayableSongs = FilterPlayableSongs(offline.modeSource, offline.statusSource)

    /** Whether a song's file deletes; every delete succeeds by default. */
    var fileDeleter: SongFileDeleter = SongFileDeleter { true }

    /** Which MediaStore songs' files delete; the user confirms the system's request for all of them by default. */
    var mediaStoreDeleter: MediaStoreSongDeleter = MediaStoreSongDeleter { songs, _ -> songs.toSet() }

    val resolveSongs = ResolveSongs(songRepository, genreRepository, playlistRepository, queueOperations, ResolveFolderSongs(songRepository), offline.statusSource)
    val playSongs = PlaySongs(queueOperations, playbackOperations)
    val shuffleSongs = ShuffleSongs(playbackOperations)
    val shuffleWorks = ShuffleWorks(queueOperations, playbackOperations)

    /** The providers radio asks for similar songs; none by default, so a radio is built from the library. */
    val similarSongsProviders = mutableSetOf<SimilarSongsProvider>()
    val startRadio = StartRadio(songRepository, genreRepository, resolveSongs, AggregateSimilarSongsProvider(similarSongsProviders), playSongs)
    val resumeContext = ResumeContext(queueOperations, playbackOperations, playHistoryRepository, resolveSongs, filterPlayableSongs, Random(0))
    val enqueueSongs = EnqueueSongs(playbackOperations, resolveSongs, filterPlayableSongs)
    val playAfterWork = PlayAfterWork(queueOperations, playbackOperations, resolveSongs, filterPlayableSongs)
    val playFromCopy = PlayFromCopy(queueOperations, playbackOperations, playSongs)
    val addToPlaylist = AddToPlaylist(playlistRepository, resolveSongs)
    val createPlaylist = CreatePlaylist(playlistRepository, resolveSongs)
    val excludeSongs = ExcludeSongs(songRepository, queueOperations, resolveSongs)
    val deleteSongs = DeleteSongs(songRepository, queueOperations, resolveSongs, { fileDeleter.delete(it) }, { songs, callerActive -> mediaStoreDeleter.delete(songs, callerActive) })
    val songDownloader = FakeSongDownloader()

    /** Whether the user may download from a server (the entitlement gate); allowed by default. */
    var downloadAllowed = true
    val downloadSongs = DownloadSongs(songDownloader, resolveSongs, TryDownloadFromServer { downloadAllowed })

    /**
     * What the real Pro gate ([ServerAccessGate.tryUse]) decides a Pro feature from: Pro by default; `Free(trialUsed =
     * true)` refuses, and `Unknown` (the store hasn't answered) lets it through. Records each feature asked for.
     */
    val entitlement = MutableStateFlow<Entitlement>(Entitlement.Pro(ProSource.Lifetime))
    val proFeaturesAsked = mutableListOf<ProFeature>()
    private val proFeatureGate = ServerAccessGate(entitlement, startTrial = null)
    val tryUseProFeature = TryUseProFeature { feature ->
        proFeaturesAsked += feature
        proFeatureGate.tryUse(feature)
    }
    val findGoToTarget = FindGoToTarget(albumRepository, albumArtistRepository)
    val shareSongs = ShareSongs(resolveSongs)
    val restorePlaylistSongs = RestorePlaylistSongs(playlistRepository)
    val favouriteSongs = FavouriteSongs(songRepository, resolveSongs)
    val observeSongs = ObserveSongs(songRepository, offline.filter)
    val observeAlbums = ObserveAlbums(albumRepository, offline.groups(songRepository))
    val observeArtists = ObserveArtists(albumArtistRepository, offline.groups(songRepository))
    val observeArtistAlbums = ObserveArtistAlbums(albumRepository, songRepository)
    val observeGenres = ObserveGenres(genreRepository, offline.groups(songRepository))
    val handler = MediaActionHandler(
        resolveSongs = resolveSongs,
        filterPlayableSongs = filterPlayableSongs,
        playSongs = playSongs,
        shuffleSongs = shuffleSongs,
        shuffleWorks = shuffleWorks,
        resumeContext = resumeContext,
        startRadio = startRadio,
        enqueueSongs = enqueueSongs,
        playAfterWork = playAfterWork,
        playFromCopy = playFromCopy,
        addToPlaylist = addToPlaylist,
        favouriteSongs = favouriteSongs,
        createPlaylist = createPlaylist,
        findGoToTarget = findGoToTarget,
        shareSongs = shareSongs,
        excludeSongs = excludeSongs,
        deleteSongs = deleteSongs,
        downloadSongs = downloadSongs,
        playlistRepository = playlistRepository,
        restorePlaylistSongs = restorePlaylistSongs,
        tryUseProFeature = tryUseProFeature,
    )
}
