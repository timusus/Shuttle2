package com.simplecityapps.shuttle.ui.screens.home.search

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
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
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val preferenceManager: GeneralPreferenceManager
) : BasePresenter<SearchContract.View>(),
    SearchContract.Presenter {

    companion object {
        private const val TAG = "SearchPresenter"

        // Performance logging disabled in production for performance
        // Set to true for development/debugging only
        private const val ENABLE_PERFORMANCE_LOGGING = false
    }

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
            // Don't call setData for empty queries - let the fragment handle the empty state
            return
        }

        val searchStartTime = if (ENABLE_PERFORMANCE_LOGGING) System.currentTimeMillis() else 0L
        if (ENABLE_PERFORMANCE_LOGGING) {
            Log.d(TAG, "=== Starting FTS-enhanced search for query: '$query' ===")
            StringComparison.resetPerformanceCounters()
        }

        queryJob =
            launch {
                // Step 1: Use FTS to get candidate sets (fast pre-filtering)
                // Step 2: Apply Jaro-Winkler similarity on candidates (accurate scoring)
                // Step 3: Sort by Jaro-Winkler score

                var artistResults: List<ArtistJaroSimilarity> = emptyList()
                if (preferenceManager.searchFilterArtists) {
                    val artistStartTime = if (ENABLE_PERFORMANCE_LOGGING) System.currentTimeMillis() else 0L
                    val ftsCandidates = artistRepository.searchAlbumArtistsFts(query, limit = 200)
                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val ftsTime = System.currentTimeMillis() - artistStartTime
                        Log.d(TAG, "FTS found ${ftsCandidates.size} artist candidates in ${ftsTime}ms")
                    }

                    artistResults = ftsCandidates
                        .map { albumArtist -> ArtistJaroSimilarity(albumArtist, query) }
                        .filter { it.compositeScore > StringComparison.threshold }
                        .sortedWith(
                            compareByDescending<ArtistJaroSimilarity> { it.compositeScore }
                                .thenBy { it.strippedNameLength }
                        )
                        .take(50) // Limit to top 50 results

                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val artistTime = System.currentTimeMillis() - artistStartTime
                        Log.d(TAG, "Artist search: ${artistResults.size}/${ftsCandidates.size} candidates matched, took ${artistTime}ms total")
                    }
                }

                var albumResults: List<AlbumJaroSimilarity> = emptyList()
                if (preferenceManager.searchFilterAlbums) {
                    val albumStartTime = if (ENABLE_PERFORMANCE_LOGGING) System.currentTimeMillis() else 0L
                    val ftsCandidates = albumRepository.searchAlbumsFts(query, limit = 400)
                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val ftsTime = System.currentTimeMillis() - albumStartTime
                        Log.d(TAG, "FTS found ${ftsCandidates.size} album candidates in ${ftsTime}ms")
                    }

                    albumResults = ftsCandidates
                        .map { album -> AlbumJaroSimilarity(album, query) }
                        .filter { it.compositeScore > StringComparison.threshold }
                        .sortedWith(
                            compareByDescending<AlbumJaroSimilarity> { it.compositeScore }
                                .thenBy { it.strippedNameLength }
                        )
                        .take(50) // Limit to top 50 results

                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val albumTime = System.currentTimeMillis() - albumStartTime
                        Log.d(TAG, "Album search: ${albumResults.size}/${ftsCandidates.size} candidates matched, took ${albumTime}ms total")
                    }
                }

                var songResults: List<SongJaroSimilarity> = emptyList()
                if (preferenceManager.searchFilterSongs) {
                    val songStartTime = if (ENABLE_PERFORMANCE_LOGGING) System.currentTimeMillis() else 0L
                    val ftsCandidates = songRepository.searchSongsFts(query, limit = 500)
                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val ftsTime = System.currentTimeMillis() - songStartTime
                        Log.d(TAG, "FTS found ${ftsCandidates.size} song candidates in ${ftsTime}ms")
                    }

                    songResults = ftsCandidates
                        .asSequence()
                        .map { song -> SongJaroSimilarity(song, query) }
                        .filter { it.compositeScore > StringComparison.threshold }
                        .sortedWith(
                            compareByDescending<SongJaroSimilarity> { it.compositeScore }
                                .thenBy { it.strippedNameLength }
                        )
                        .take(50) // Limit to top 50 results
                        .toList()

                    if (ENABLE_PERFORMANCE_LOGGING) {
                        val songTime = System.currentTimeMillis() - songStartTime
                        Log.d(TAG, "Song search: ${songResults.size}/${ftsCandidates.size} candidates matched, took ${songTime}ms total")
                    }
                }

                val results = Triple(artistResults, albumResults, songResults)
                searchResult = results
                view?.setData(results)

                if (ENABLE_PERFORMANCE_LOGGING) {
                    val totalSearchTime = System.currentTimeMillis() - searchStartTime
                    Log.d(TAG, "=== FTS-enhanced search completed in ${totalSearchTime}ms ===")
                    Log.d(TAG, "Results: ${results.first.size} artists, ${results.second.size} albums, ${results.third.size} songs")
                    StringComparison.logPerformanceStats()
                }
            }
        this.query = query
    }

    override fun play(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun play(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun play(song: Song) {
        launch {
            val songs = searchResult.third.map { it.song }
            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun playNext(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun exclude(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
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
