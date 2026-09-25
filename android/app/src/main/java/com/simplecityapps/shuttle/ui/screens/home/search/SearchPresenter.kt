package com.simplecityapps.shuttle.ui.screens.home.search

import android.content.Context
import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface SearchContract : BaseContract.Presenter<SearchContract.View> {
    interface View {
        fun setData(searchResult: Triple<List<ArtistJaroSimilarity>, List<AlbumJaroSimilarity>, List<SongJaroSimilarity>>)

        fun showLoadError(error: Error)

        fun onAddedToQueue(albumArtist: AlbumArtist)

        fun onAddedToQueue(album: Album)

        fun onAddedToQueue(song: Song)

        fun showDeleteError(error: Error)

        fun showTagEditor(songs: List<Song>)

        fun updateFilters(
            artists: Boolean,
            albums: Boolean,
            songs: Boolean
        )

        fun updateQuery(query: String?)
    }

    interface Presenter {
        fun loadData(query: String)

        fun play(albumArtist: AlbumArtist)

        fun addToQueue(albumArtist: AlbumArtist)

        fun playNext(albumArtist: AlbumArtist)

        fun exclude(albumArtist: AlbumArtist)

        fun editTags(albumArtist: AlbumArtist)

        fun play(album: Album)

        fun addToQueue(album: Album)

        fun playNext(album: Album)

        fun exclude(album: Album)

        fun editTags(album: Album)

        fun play(song: Song)

        fun addToQueue(song: Song)

        fun playNext(song: Song)

        fun exclude(song: Song)

        fun delete(song: Song)

        fun editTags(song: Song)

        fun updateFilters(
            artists: Boolean,
            albums: Boolean,
            songs: Boolean
        )
    }
}

class SearchPresenter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val preferenceManager: GeneralPreferenceManager,
    private val resolveSongs: ResolveSongs,
    private val playSongs: PlaySongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
) : BasePresenter<SearchContract.View>(),
    SearchContract.Presenter {
    private var query: String? = null

    private var searchResult: Triple<List<ArtistJaroSimilarity>, List<AlbumJaroSimilarity>, List<SongJaroSimilarity>> =
        Triple(emptyList(), emptyList(), emptyList())

    private var queryJob: Job? = null

    override fun bindView(view: SearchContract.View) {
        super.bindView(view)

        view.updateFilters(preferenceManager.searchFilterArtists, preferenceManager.searchFilterAlbums, preferenceManager.searchFilterSongs)
    }

    override fun loadData(query: String) {
        queryJob?.cancel()
        if (query.isEmpty()) {
            this.query = query
            view?.setData(Triple(emptyList(), emptyList(), emptyList()))
            return
        }
        queryJob =
            launch {
                var artistResults: Flow<List<ArtistJaroSimilarity>> = flowOf(emptyList())
                if (preferenceManager.searchFilterArtists) {
                    artistResults =
                        artistRepository.getAlbumArtists(AlbumArtistQuery.All())
                            .map { albumArtists ->
                                albumArtists
                                    .map { albumArtist -> ArtistJaroSimilarity(albumArtist, query) }
                                    .filter { it.albumArtistNameJaroSimilarity.score > StringComparison.threshold || it.artistNameJaroSimilarity.score > StringComparison.threshold }
                                    .sortedByDescending { if (it.albumArtistNameJaroSimilarity.score > StringComparison.threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { if (it.artistNameJaroSimilarity.score > StringComparison.threshold) it.artistNameJaroSimilarity.score else 0.0 }
                            }
                }

                var albumResults: Flow<List<AlbumJaroSimilarity>> = flowOf(emptyList())
                if (preferenceManager.searchFilterAlbums) {
                    albumResults =
                        albumRepository.getAlbums(AlbumQuery.All())
                            .map { albums ->
                                albums.map { album -> AlbumJaroSimilarity(album, query) }
                                    .filter { it.nameJaroSimilarity.score > StringComparison.threshold || it.albumArtistNameJaroSimilarity.score > StringComparison.threshold || it.artistNameJaroSimilarity.score > StringComparison.threshold }
                                    .sortedByDescending { if (it.albumArtistNameJaroSimilarity.score > StringComparison.threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { if (it.artistNameJaroSimilarity.score > StringComparison.threshold) it.artistNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { it.nameJaroSimilarity.score }
                            }
                }

                var songResults: Flow<List<SongJaroSimilarity>> = flowOf(emptyList())
                if (preferenceManager.searchFilterSongs) {
                    songResults =
                        songRepository.getSongs(SongQuery.All())
                            .map { songs ->
                                songs.orEmpty()
                                    .asSequence()
                                    .map { song -> SongJaroSimilarity(song, query) }
                                    .filter { it.nameJaroSimilarity.score > StringComparison.threshold || it.albumArtistNameJaroSimilarity.score > StringComparison.threshold || it.artistNameJaroSimilarity.score > StringComparison.threshold || it.albumNameJaroSimilarity.score > StringComparison.threshold }
                                    .sortedByDescending { if (it.albumArtistNameJaroSimilarity.score > StringComparison.threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { if (it.artistNameJaroSimilarity.score > StringComparison.threshold) it.albumArtistNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { if (it.albumNameJaroSimilarity.score > StringComparison.threshold) it.albumNameJaroSimilarity.score else 0.0 }
                                    .sortedByDescending { if (it.nameJaroSimilarity.score > StringComparison.threshold) it.nameJaroSimilarity.score else 0.0 }.toList()
                            }
                }

                combine(artistResults, albumResults, songResults) { artists, albums, songs ->
                    Triple(artists, albums, songs)
                }
                    .flowOn(Dispatchers.IO)
                    .collect { results ->
                        searchResult = results
                        view?.setData(results)
                    }
            }
        this.query = query
    }

    override fun play(albumArtist: AlbumArtist) {
        launch { play(resolveSongs(MediaSelection.AlbumArtists(albumArtist))) }
    }

    override fun play(album: Album) {
        launch { play(resolveSongs(MediaSelection.Albums(album))) }
    }

    override fun play(song: Song) {
        launch {
            val songs = searchResult.third.map { it.song }
            play(songs, songs.indexOf(song))
        }
    }

    private suspend fun play(songs: List<Song>, position: Int = 0) {
        if (songs.isEmpty()) return
        val result = playSongs(songs, position.coerceAtLeast(0))
        if (result is PlaySongs.Result.Failure && result.message != null) view?.showLoadError(Error(result.message))
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        enqueue(MediaSelection.AlbumArtists(albumArtist), EnqueueSongs.Position.End) { view?.onAddedToQueue(albumArtist) }
    }

    override fun playNext(albumArtist: AlbumArtist) {
        enqueue(MediaSelection.AlbumArtists(albumArtist), EnqueueSongs.Position.Next) { view?.onAddedToQueue(albumArtist) }
    }

    override fun addToQueue(album: Album) {
        enqueue(MediaSelection.Albums(album), EnqueueSongs.Position.End) { view?.onAddedToQueue(album) }
    }

    override fun playNext(album: Album) {
        enqueue(MediaSelection.Albums(album), EnqueueSongs.Position.Next) { view?.onAddedToQueue(album) }
    }

    override fun addToQueue(song: Song) {
        enqueue(MediaSelection.Songs(song), EnqueueSongs.Position.End) { view?.onAddedToQueue(song) }
    }

    override fun playNext(song: Song) {
        enqueue(MediaSelection.Songs(song), EnqueueSongs.Position.Next) { view?.onAddedToQueue(song) }
    }

    private fun enqueue(selection: MediaSelection, position: EnqueueSongs.Position, onAdded: () -> Unit) {
        launch {
            enqueueSongs(selection, position)
            onAdded()
        }
    }

    override fun exclude(albumArtist: AlbumArtist) {
        launch { excludeSongs(MediaSelection.AlbumArtists(albumArtist)) }
    }

    override fun exclude(album: Album) {
        launch { excludeSongs(MediaSelection.Albums(album)) }
    }

    override fun exclude(song: Song) {
        launch { excludeSongs(MediaSelection.Songs(song)) }
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch { view?.showTagEditor(resolveSongs(MediaSelection.AlbumArtists(albumArtist))) }
    }

    override fun editTags(album: Album) {
        launch { view?.showTagEditor(resolveSongs(MediaSelection.Albums(album))) }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun delete(song: Song) {
        launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) {
                view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
            }
        }
    }

    override fun updateFilters(
        artists: Boolean,
        albums: Boolean,
        songs: Boolean
    ) {
        preferenceManager.searchFilterArtists = artists
        preferenceManager.searchFilterAlbums = albums
        preferenceManager.searchFilterSongs = songs
        query?.let { query ->
            loadData(query)
        }
    }
}
