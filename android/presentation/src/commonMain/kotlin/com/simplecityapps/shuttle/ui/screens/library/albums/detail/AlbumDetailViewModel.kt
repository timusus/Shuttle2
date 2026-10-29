package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumSources
import com.simplecityapps.shuttle.model.FindOtherRecordings
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.OtherRecording
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.albumSources
import com.simplecityapps.shuttle.model.isClassicalBlock
import com.simplecityapps.shuttle.model.performersLine
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveCopyContext
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.SetAlbumPrimarySource
import com.simplecityapps.shuttle.ui.actions.postSourceChoiceFailure
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
    /** The album artwork's seed, which tints the screen when Colour from artwork is on. */
    val seed: ArtworkSeed = ArtworkSeed.None,
    /** The album artist's other albums, newest first, for the More by shelf; empty when the album has no album artist or is a compilation. */
    val moreByArtist: List<Album> = emptyList(),
    /** The album artists [moreByArtist] is from, by name in the album's order ("A", "B" for "A feat. B"), for the shelf's title. */
    val moreByArtistNames: List<String> = emptyList(),
    /** Classical display is on: the screen groups movements under their work. */
    val classical: Boolean = false,
    /** The classical page's rows; null when classical display is off or the album has no work blocks, which then render as plain songs. */
    val rows: AlbumRows? = null,
    /** Other albums' recordings of each of this album's works, by the work's first song id; works with none are absent. */
    val otherRecordings: Map<Long, List<OtherRecording>> = emptyMap(),
    /** The album's sources; null unless merging is on and the songs span two or more sources. */
    val sources: AlbumSources? = null,
    /** The Primary source picker is open; only while [sources] offers a choice. */
    val choosingSource: Boolean = false,
    /** Failures to show once, as a snackbar. */
    val events: List<PendingEvent<MediaActionMessage>> = emptyList(),
) {
    // Swift sees no default arguments.
    constructor(
        album: Album?,
        songs: List<Song>,
        currentSong: Song?,
        loadingState: LoadingState,
        seed: ArtworkSeed,
        moreByArtist: List<Album>,
        moreByArtistNames: List<String>,
        classical: Boolean,
    ) : this(album, songs, currentSong, loadingState, seed, moreByArtist, moreByArtistNames, classical, null, emptyMap(), null, false, emptyList())

    /** The first conductor or performer credit among the songs, for the subtitle; null when classical display is off, the songs aren't a classical block or none is tagged. */
    val performers: String? get() = if (classical && songs.isClassicalBlock()) songs.firstNotNullOfOrNull { it.performersLine() } else null

    /** How many sources the subtitle shows; null unless [sources] offers a choice (two or more, merging on). */
    val sourceCount: Int? get() = sources?.sourceCount

    val playContext: PlayContext get() = album?.playContext ?: PlayContext.None

    enum class LoadingState { Loading, Ready, Empty }
}

/**
 * One album's songs and header, loaded by [groupKey], the key its route carries. The screen's actions go
 * through its MediaActionsHost, so this only derives state.
 */
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumGroupKey?,
    observeSongs: ObserveSongs,
    observeAlbums: ObserveAlbums,
    observeArtists: ObserveArtists,
    observeCurrentSong: ObserveCurrentSong,
    observeArtworkSeed: ObserveArtworkSeed,
    observeSetting: ObserveSetting,
    findOtherRecordings: FindOtherRecordings,
    observeCopyContext: ObserveCopyContext,
    private val setAlbumPrimarySource: SetAlbumPrimarySource,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(groupKey: AlbumGroupKey?): AlbumDetailViewModel
    }

    private val songs = observeSongs(SongQuery.AlbumGroupKey(key = groupKey))
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val album = observeAlbums(AlbumQuery.AlbumGroupKey(groupKey))
        .map { it.firstOrNull() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val classicalDisplay = observeSetting(AppearanceSettings.ClassicalDisplay)

    private val classicalRows = ClassicalRows(songs, album, classicalDisplay, observeSongs, findOtherRecordings, ioDispatcher)

    private val moreBy = observeMoreBy(album, groupKey, observeAlbums, observeArtists)

    fun onToggleWork(firstSongId: Long) {
        classicalRows.toggleWork(firstSongId)
    }

    // Songs only carry several copies while merging is on, so a choice of sources implies it.
    private val sources = combine(songs, observeCopyContext()) { songs, context ->
        albumSources(songs, context.preferences.primaryAlbums).takeIf { context.mergeCopies && it.hasChoice }
    }

    private val choosingSource = MutableStateFlow(false)

    fun onChooseSource() {
        choosingSource.value = true
    }

    fun onDismissSourcePicker() {
        choosingSource.value = false
    }

    /** Makes [provider] the album's primary source; null is Automatic. */
    fun onSetPrimarySource(provider: MediaProviderType?) {
        choosingSource.value = false
        viewModelScope.launch { events.postSourceChoiceFailure(logger) { setAlbumPrimarySource(songs.first(), provider) } }
    }

    private val events = PendingEvents<MediaActionMessage>()

    fun onEventHandled(id: Long) = events.consume(id)

    private class Extras(
        val moreBy: MoreBy,
        val classical: Boolean,
        val grouped: Grouped,
        val sources: AlbumSources?,
        val choosingSource: Boolean,
        val events: List<PendingEvent<MediaActionMessage>>,
    )

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songs,
        album,
        observeCurrentSong(),
        observeArtworkSeed(songs.map { it.firstOrNull() }),
        combine(moreBy, classicalDisplay, classicalRows.grouped, combine(sources, choosingSource, events.flow, ::Triple)) { moreBy, classical, grouped, (sources, choosing, events) ->
            Extras(moreBy, classical, grouped, sources, choosing, events)
        },
    ) { _, album, currentSong, seed, extras ->
        val songs = extras.grouped.songs
        AlbumDetailUiState(
            album = album,
            songs = songs,
            currentSong = currentSong,
            loadingState = if (songs.isEmpty()) {
                AlbumDetailUiState.LoadingState.Empty
            } else {
                AlbumDetailUiState.LoadingState.Ready
            },
            seed = seed,
            moreByArtist = extras.moreBy.albums,
            // A credited key with no artist row or a blank name resolves to nothing: fall back to the raw tag
            moreByArtistNames = extras.moreBy.names.ifEmpty { listOfNotNull(album?.albumArtist?.takeIf { it.isNotBlank() }) },
            classical = extras.classical,
            rows = extras.grouped.rows?.withPlaying(currentSong?.id),
            otherRecordings = extras.grouped.otherRecordings,
            sources = extras.sources,
            choosingSource = extras.choosingSource && extras.sources != null,
            events = extras.events,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )
}

private val logger = Logger.tagged("AlbumDetailViewModel")
