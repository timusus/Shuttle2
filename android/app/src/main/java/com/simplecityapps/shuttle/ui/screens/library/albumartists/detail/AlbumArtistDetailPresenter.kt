package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AlbumArtistDetailContract {
    interface View {
        fun setListData(albumSongsMap: Map<Album, List<Song>>)

        fun showLoadError(error: Error)

        fun onAddedToQueue(name: String)

        fun setAlbumArtist(albumArtist: AlbumArtist)

        fun showDeleteError(error: Error)

        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()

        fun onSongClicked(
            song: Song,
            songs: List<Song>
        )

        fun onSongClicked(song: Song)

        fun play()

        fun shuffle()

        fun shuffleAlbums()

        fun addToQueue(albumArtist: AlbumArtist)

        fun play(album: Album)

        fun addToQueue(album: Album)

        fun addToQueue(song: Song)

        fun playNext(album: AlbumArtist)

        fun playNext(album: Album)

        fun playNext(song: Song)

        fun exclude(song: Song)

        fun editTags(song: Song)

        fun exclude(album: Album)

        fun editTags(album: Album)

        fun editTags(albumArtist: AlbumArtist)

        fun delete(song: Song)
    }
}

class AlbumArtistDetailPresenter
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val albumArtistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val albumArtist: AlbumArtist
) : BasePresenter<AlbumArtistDetailContract.View>(),
    AlbumArtistDetailContract.Presenter {
    @AssistedFactory
    interface Factory {
        fun create(albumArtist: AlbumArtist): AlbumArtistDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun bindView(view: AlbumArtistDetailContract.View) {
        super.bindView(view)

        view.setAlbumArtist(albumArtist)

        launch {
            albumArtistRepository
                .getAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = albumArtist.groupKey))
                .collect { albumArtists ->
                    albumArtists.firstOrNull()?.let { albumArtist ->
                        this@AlbumArtistDetailPresenter.view?.setAlbumArtist(albumArtist)
                    }
                }
        }
    }

    override fun loadData() {
        launch {
            albumRepository.getAlbums(AlbumQuery.ArtistGroupKey(albumArtist.groupKey))
                .combine(
                    songRepository
                        .getSongs(
                            SongQuery.ArtistGroupKeys(
                                listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))
                            )
                        )
                        .filterNotNull()
                ) { albums, songs ->
                    albums.map { album -> Pair(album, songs.filter { song -> song.albumGroupKey == album.groupKey }) }
                        .sortedWith { a, b -> b.first.year?.compareTo(a.first.year ?: 0) ?: 0 }
                        .toMap()
                }
                .collect { map ->
                    this@AlbumArtistDetailPresenter.songs = map.values.flatten()
                    view?.setListData(map)
                }
        }
    }

    override fun onSongClicked(song: Song) {
        onSongClicked(song, this.songs)
    }

    override fun onSongClicked(
        song: Song,
        songs: List<Song>
    ) {
        launch {
            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun play() {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
                    .firstOrNull()
                    .orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun shuffle() {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
                    .firstOrNull()
                    .orEmpty()
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun shuffleAlbums() {
        launch {
            val albums =
                songRepository
                    .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
                    .firstOrNull()
                    .orEmpty()
                    .groupBy { it.album }

            val songs =
                albums.keys.shuffled().flatMap { key ->
                    albums.getValue(key)
                }

            if (queueManager.setQueue(songs)) {
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
            view?.onAddedToQueue(albumArtist.name ?: albumArtist.friendlyArtistName ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun playNext(album: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist.name ?: albumArtist.friendlyArtistName ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
            queueManager.remove(queueManager.getQueue().filter { queueItem -> songs.contains(queueItem.song) })
        }
    }

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
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
}
