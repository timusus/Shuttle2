package com.simplecityapps.shuttle.ui.shell.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.PlayerSettings
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveFavouriteSongIds
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ToggleFavourite
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.home.HomeItem
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the Cast framework could start on this device. */
fun interface CastAvailability {
    fun isAvailable(): Boolean
}

/** The song the saved queue was left on (see [NowPlayingSnapshot]), shown until the queue is restored. */
fun interface SavedNowPlaying {
    fun snapshot(): NowPlayingSnapshot?
}

/** A queued song, each time it's skipped because streaming it needs S2 Pro. */
fun interface ObserveGatedServerSkip {
    operator fun invoke(): Flow<Song>
}

/**
 * The stored ReplayGain mode setting. The setting lives with the playback settings in `:android:playback:core`, a data
 * module this layer can't see, so each platform's graph hands it over.
 */
fun interface ReplayGainModeSetting {
    fun setting(): Setting<ReplayGainMode>
}

/**
 * The player surfaces' state and actions (docs/architecture/app-shell.md, sections 1 and 5): the
 * queue, playback state and modes, favourite, sleep timer, speed and ReplayGain, the now-playing
 * artwork seed, and the panel Now Playing's bar has open, which survives process death. Its
 * uiState carries the playback position beside the rest (see [PlayerScreenState]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @AssistedInject constructor(
    observeQueue: ObserveQueue,
    observeQueueSource: ObserveQueueSource,
    observePlayback: ObservePlayback,
    observeProgress: ObserveProgress,
    observeGatedServerSkip: ObserveGatedServerSkip,
    private val controlPlayback: ControlPlayback,
    private val editQueue: EditQueue,
    observeFavouriteSongIds: ObserveFavouriteSongIds,
    private val setFavourite: ToggleFavourite,
    private val observePlaylists: ObservePlaylists,
    private val controlSleepTimer: ControlSleepTimer,
    private val readSleepTimeRemaining: ReadSleepTimeRemaining,
    readSleepTimerPlayToEnd: ReadSleepTimerPlayToEnd,
    observeSetting: ObserveSetting,
    private val saveSetting: SaveSetting,
    replayGainModeSetting: ReplayGainModeSetting,
    private val setReplayGainMode: SetReplayGainMode,
    observeArtworkSeed: ObserveArtworkSeed,
    castAvailability: CastAvailability,
    savedNowPlaying: SavedNowPlaying,
    private val clearQueue: ClearQueue,
    private val restoreQueue: RestoreQueue,
    private val availableMediaActions: AvailableMediaActions,
    private val mediaActionHandler: MediaActionHandler,
    deliveredFormats: DeliveredFormats,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(),
    PlayerActions {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(PlayerViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): PlayerViewModel = create(extras.createSavedStateHandle())

        fun create(
            @Assisted savedStateHandle: SavedStateHandle
        ): PlayerViewModel
    }

    private val castAvailable = castAvailability.isAvailable()

    // Shown on a cold start until the saved queue is restored, then the restored queue's own song takes over.
    private val savedSnapshot: NowPlayingSnapshot? = savedNowPlaying.snapshot()
    private val savedSong: PlayerSong? = savedSnapshot?.toPlayerSong()

    /** Bumped when the sleep timer is started or stopped here. */
    private val sleepTimerChanges = MutableStateFlow(0)
    private val sleepTimerPlayToEnd = MutableStateFlow(readSleepTimerPlayToEnd())

    private val events = PendingEvents<PlayerUiEvent>()

    private var clearedQueue: QueueSnapshot? = null

    private val panel: StateFlow<NowPlayingPanel?> = savedStateHandle.getStateFlow(PANEL_KEY, null)

    private var removedItem: RemovedQueueItem? = null

    init {
        viewModelScope.launch {
            observeGatedServerSkip().collect { song -> events.post(PlayerUiEvent.ServerSongSkipped(song.name.orEmpty())) }
        }
    }

    private val queue: StateFlow<QueueState> = observeQueue()

    private val currentSong: Flow<Song?> = queue.map { it.currentItem?.song }.distinctUntilChanged()

    private val favouriteIds: Flow<Set<Long>> = observeFavouriteSongIds()

    private val seed: Flow<ArtworkSeed> = observeArtworkSeed(currentSong, observeSetting(ArtworkSettings.NowPlayingArtworkSource))

    // Ticks only while a timer runs, so it notices the timer going off (the timer has no flow of its own).
    private val sleepTimerActive: Flow<Boolean> =
        sleepTimerChanges
            .flatMapLatest {
                if (readSleepTimeRemaining() == null) {
                    flowOf(false)
                } else {
                    flow {
                        while (readSleepTimeRemaining() != null) {
                            emit(true)
                            delay(SLEEP_TIMER_TICK_MS)
                        }
                        emit(false)
                    }
                }
            }.distinctUntilChanged()

    // None until it's resolved, so resolving it never holds the rest of the player back.
    private val queueSource: Flow<HomeItem?> = observeQueueSource(queue).onStart { emit(null) }

    private val extras: Flow<Extras> =
        combine(
            favouriteIds,
            seed,
            sleepTimerActive,
            sleepTimerPlayToEnd,
            observeSetting(replayGainModeSetting.setting()),
        ) { favourites, seed, sleeping, playToEnd, replayGainMode ->
            Extras(favourites, seed, sleeping, playToEnd, replayGainMode)
        }

    private val player: Flow<PlayerUiState> =
        combine(queue, observePlayback(), extras) { queue, playback, extras ->
            queue.toPlayerUiState(savedSong).copy(
                playing = playback.state == PlaybackState.Playing,
                buffering = playback.state == PlaybackState.Loading,
                shuffle = playback.shuffleMode == ShuffleMode.On,
                repeatMode = playback.repeatMode.toS2RepeatMode(),
                favourite = queue.currentItem?.song?.id?.let { it in extras.favouriteIds } ?: false,
                sleepTimerActive = extras.sleepTimerActive,
                sleepTimerPlayToEnd = extras.sleepTimerPlayToEnd,
                castAvailable = castAvailable,
                castDevice = playback.castDevice,
                seed = extras.seed,
                playbackSpeed = playback.speed,
                replayGainMode = extras.replayGainMode,
            )
        }.combine(observeSetting(PlayerSettings.ShowRemainingTime)) { state, showRemainingTime ->
            state.copy(showRemainingTime = showRemainingTime)
        }.combine(observeSetting(ArtworkSettings.NowPlayingArtworkSource)) { state, image ->
            state.copy(nowPlayingImage = image)
        }.combine(deliveredFormats.byPath) { state, delivered ->
            state.copy(delivered = state.current?.song?.path?.let(delivered::get))
        }.combine(panel) { state, panel ->
            // An emptied queue takes the player, and its panel, away.
            state.copy(panel = panel.takeIf { state.hasQueue == true })
        }.combine(queueSource) { state, source ->
            state.copy(queueSource = source)
        }.distinctUntilChanged()

    // The total is the song's own duration, as the queue rows show it; the player's reported duration can differ by a rounding second.
    private val progress: Flow<PlayerProgress> =
        combine(observeProgress(), currentSong) { progress, song ->
            val songDuration = song?.duration?.toLong()?.takeIf { it > 0 }
            when {
                progress != null -> PlayerProgress(progress.position.toLong(), songDuration ?: progress.duration.toLong())
                song != null -> PlayerProgress(song.playbackPosition.toLong(), song.duration.toLong())
                else -> savedProgress() ?: PlayerProgress.Zero
            }
        }.distinctUntilChanged()

    // A tick only replaces the progress: the player state stays the same instance, so its readers skip the tick.
    val uiState: StateFlow<PlayerScreenState> =
        combine(player, progress, events.flow, ::PlayerScreenState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlayerScreenState(queue.value.toPlayerUiState(savedSong), savedProgress() ?: PlayerProgress.Zero),
            )

    /** Where the saved song was left, while it stands in for a queue not yet restored. */
    private fun savedProgress(): PlayerProgress? = savedSnapshot
        ?.takeIf { queue.value.isAwaitingRestore }
        ?.let { snapshot -> PlayerProgress(snapshot.positionMs.toLong(), snapshot.durationMs.toLong()) }

    // Nothing is loaded yet while the saved song stands in for the queue: play once the queue is restored.
    override fun togglePlayback() = control(
        if (savedSnapshot != null && queue.value.isAwaitingRestore) PlaybackCommand.PlayWhenRestored else PlaybackCommand.TogglePlayback,
    )

    override fun skipToNext() = control(PlaybackCommand.SkipToNext)

    override fun skipToPrevious() = control(PlaybackCommand.SkipToPrevious)

    override fun seekTo(positionMs: Long) = control(PlaybackCommand.SeekTo(positionMs))

    override fun toggleShuffle() = control(PlaybackCommand.ToggleShuffle)

    override fun cycleRepeatMode() = control(PlaybackCommand.CycleRepeatMode)

    override fun toggleFavourite() {
        val song = queue.value.currentItem?.song ?: return
        val favourite = uiState.value.player.favourite
        viewModelScope.launch { setFavourite(song, favourite) }
    }

    override fun startSleepTimer(
        durationMs: Long,
        playToEnd: Boolean,
    ) {
        controlSleepTimer(SleepTimerCommand.Start(durationMs, playToEnd))
        sleepTimerPlayToEnd.value = playToEnd
        sleepTimerChanges.value++
    }

    override fun stopSleepTimer() {
        controlSleepTimer(SleepTimerCommand.Stop)
        sleepTimerChanges.value++
    }

    override fun sleepTimerRemaining(): Flow<Long?> = flow {
        while (true) {
            emit(readSleepTimeRemaining())
            delay(SLEEP_TIMER_TICK_MS)
        }
    }.distinctUntilChanged()

    override fun setShowRemainingTime(show: Boolean) = saveSetting(PlayerSettings.ShowRemainingTime, show)

    override fun setPlaybackSpeed(speed: Float) = control(PlaybackCommand.SetSpeed(speed))

    override fun setReplayGainMode(mode: ReplayGainMode) {
        viewModelScope.launch { setReplayGainMode.invoke(mode) }
    }

    override fun togglePanel(panel: NowPlayingPanel) = showPanel(panel.takeUnless { it == this.panel.value })

    override fun showPanel(panel: NowPlayingPanel?) {
        savedStateHandle[PANEL_KEY] = panel
    }

    override fun skipToQueueItem(uid: Long) = edit(QueueEdit.SkipTo(uid))

    override fun moveQueueItem(
        uid: Long,
        afterUid: Long?,
    ) = edit(QueueEdit.Move(uid, afterUid))

    override fun removeQueueItem(uid: Long) {
        val items = queue.value.items
        val index = items.indexOfFirst { it.uid == uid }
        if (index < 0) return
        removedItem = RemovedQueueItem(items[index].song, index)
        viewModelScope.launch {
            editQueue(QueueEdit.Remove(uid))
            events.post(PlayerUiEvent.QueueItemRemoved)
        }
    }

    override fun undoRemoveQueueItem() {
        val removed = removedItem ?: return
        removedItem = null
        edit(QueueEdit.Reinsert(removed.song, removed.index))
    }

    override fun playNext(uid: Long) = edit(QueueEdit.PlayNext(uid))

    override fun clearQueue() {
        val snapshot = clearQueue.invoke() ?: return
        clearedQueue = snapshot
        events.post(PlayerUiEvent.QueueCleared(snapshot.songs.size))
    }

    override fun undoClearQueue() {
        val snapshot = clearedQueue ?: return
        clearedQueue = null
        viewModelScope.launch { restoreQueue(snapshot) }
    }

    override fun songActions(song: Song): Flow<List<MediaActionType>> = availableMediaActions(MediaSelection.Songs(song)).map { types -> types.filter { it in PlayerSongActions } }

    override fun playlists(): Flow<List<Playlist>> = observePlaylists()

    override fun onMediaAction(action: MediaAction) {
        viewModelScope.launch { events.post(PlayerUiEvent.MediaActionDone(mediaActionHandler.handle(action))) }
    }

    fun onEventHandled(id: Long) = events.consume(id)

    private fun control(command: PlaybackCommand) {
        viewModelScope.launch { controlPlayback(command) }
    }

    private fun edit(edit: QueueEdit) {
        viewModelScope.launch { editQueue(edit) }
    }

    private class RemovedQueueItem(
        val song: Song,
        val index: Int,
    )

    private data class Extras(
        val favouriteIds: Set<Long>,
        val seed: ArtworkSeed,
        val sleepTimerActive: Boolean,
        val sleepTimerPlayToEnd: Boolean,
        val replayGainMode: ReplayGainMode,
    )

    internal companion object {
        private const val SLEEP_TIMER_TICK_MS = 1_000L

        const val PANEL_KEY = "now_playing_panel"

        /**
         * The shared actions that make sense on the playing song or a queue row: not Play, Shuffle or the queue verbs,
         * which would rebuild or duplicate the queue it is already in (redesign inventory, section 4).
         */
        private val PlayerSongActions = setOf(
            MediaActionType.AddToPlaylist,
            MediaActionType.GoToAlbum,
            MediaActionType.GoToArtist,
            MediaActionType.EditTags,
            MediaActionType.SongInfo,
            MediaActionType.Exclude,
        )
    }
}

/** Nothing in the queue yet, with the saved queue still to be restored: the saved song stands in for it meanwhile. */
private val QueueState.isAwaitingRestore: Boolean get() = items.isEmpty() && !isRestored

/** Until the queue is restored, [savedSong] (the song it was left on) stands in for it, when there is one. */
internal fun QueueState.toPlayerUiState(savedSong: PlayerSong? = null): PlayerUiState {
    if (isAwaitingRestore) {
        return savedSong?.let { PlayerUiState(hasQueue = true, current = it, items = listOf(it)) } ?: PlayerUiState.Unknown
    }
    val currentIndex = currentPosition ?: -1
    val rows = items.mapIndexed { index, item ->
        item.song.toPlayerSong(
            uid = item.uid,
            position = when {
                item.isCurrent -> QueuePosition.Current
                index < currentIndex -> QueuePosition.Played
                else -> QueuePosition.Upcoming
            },
        )
    }
    return PlayerUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull { it.position == QueuePosition.Current }, items = rows)
}

private fun Song.toPlayerSong(
    uid: Long,
    position: QueuePosition,
) = PlayerSong(
    uid = uid,
    title = name.orEmpty(),
    artist = friendlyArtistName ?: albumArtist,
    album = album,
    durationMs = duration,
    position = position,
    song = this,
)

/** The saved song, as the current and only row of a queue not yet restored; no queue row has its uid. */
internal fun NowPlayingSnapshot.toPlayerSong(): PlayerSong = toSong().toPlayerSong(uid = -1, position = QueuePosition.Current)

private fun RepeatMode.toS2RepeatMode(): S2RepeatMode = when (this) {
    RepeatMode.Off -> S2RepeatMode.Off
    RepeatMode.All -> S2RepeatMode.All
    RepeatMode.One -> S2RepeatMode.One
}
