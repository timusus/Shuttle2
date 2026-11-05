package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.PlaylistSortKey
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSection
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenreDetailUiState(
    val genre: Genre? = null,
    /** Every song in [sort]'s order across all [sections], collapsed ones included: the play order. */
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
    val sort: SongListSort = PlaylistKind.Genre.defaultSort,
    /** The song list as shown: one section per album for the album sorts, else a single section with no album. */
    val sections: List<SongSection> = emptyList(),
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    val events: List<PendingEvent<GenreDetailEvent>> = emptyList(),
) {
    /** Swift sees no default arguments, so the iOS screen and its tests keep building a flat state with this. */
    constructor(genre: Genre?, songs: List<Song>, currentSong: Song?, loading: Boolean) : this(
        genre = genre,
        songs = songs,
        currentSong = currentSong,
        loading = loading,
        sections = if (songs.isEmpty()) emptyList() else listOf(SongSection(album = null, songs = songs)),
    )

    val kind: PlaylistKind get() = PlaylistKind.Genre

    val playContext: PlayContext get() = genre?.playContext ?: PlayContext.None

    val allAlbumsExpanded: Boolean get() = sections.allAlbumsExpanded(expandedAlbums)
}

sealed interface GenreDetailEvent {
    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleAlbumsFailed(val reason: String?) : GenreDetailEvent
}

/** One genre's songs, loaded by the genre's name, in the sort shared by every genre. */
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted genreName: String,
    observeGenres: ObserveGenres,
    genreRepository: GenreRepository,
    observeCurrentSong: ObserveCurrentSong,
    shuffleAlbums: ShuffleAlbums,
    readSetting: ReadLibraryViewSetting,
    saveSetting: SaveLibraryViewSetting,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(genreName: String): GenreDetailViewModel
    }

    private val events = PendingEvents<GenreDetailEvent>()

    private val collection = SongCollection(
        items = genreRepository.getSongsForGenre(genreName, SongQuery.All()),
        songOf = { it },
        kind = PlaylistKind.Genre,
        key = PlaylistSortKey.GENRE,
        readSetting = readSetting,
        saveSetting = saveSetting,
        shuffleAlbums = shuffleAlbums,
    )

    private val genre = observeGenres(GenreQuery.GenreName(genreName)).map { it.firstOrNull() }

    val uiState: StateFlow<GenreDetailUiState> = combine(
        genre,
        collection.view,
        observeCurrentSong(),
        events.flow,
    ) { genre, view, currentSong, events ->
        GenreDetailUiState(
            genre = genre,
            songs = view.items,
            currentSong = currentSong,
            loading = false,
            sort = view.sort,
            sections = view.sections,
            expandedAlbums = view.expandedAlbums,
            events = events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreDetailUiState())

    fun onSortSelected(sort: SongListSort) = collection.select(sort)

    fun onToggleAlbum(album: Album) = collection.toggleAlbum(album, uiState.value.sections)

    fun onExpandAll() = collection.expandAll(uiState.value.sections)

    fun onCollapseAll() = collection.collapseAll()

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val failure = collection.shuffle(uiState.value.songs) ?: return@launch
            events.post(GenreDetailEvent.ShuffleAlbumsFailed(failure.message))
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
