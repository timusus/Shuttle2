package com.simplecityapps.shuttle.ui.shell.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Loads the seed colour of a song's artwork for the player's artwork scheme. */
fun interface ArtworkSeedSource {
    suspend fun seedFor(song: Song): ArtworkSeed
}

/** Whether the Cast framework could start on this device. */
fun interface CastAvailability {
    fun isAvailable(): Boolean
}

/** The song the saved queue was left on (see [NowPlayingSnapshot]), shown until the queue is restored. */
fun interface SavedNowPlaying {
    fun snapshot(): NowPlayingSnapshot?
}

/** The sleep timer's remembered "play last song to end" choice. */
interface SleepTimerPreference {
    var playToEnd: Boolean
}

/** The stored ReplayGain mode, which the player's Playback & sound sheet shows and changes. */
interface ReplayGainPreference {
    val mode: Flow<ReplayGainMode>

    /** Stores [mode] and applies it to the live audio processor. */
    fun set(mode: ReplayGainMode)
}

/**
 * The player surfaces' state and actions (docs/architecture/app-shell.md, sections 1 and 5): the
 * queue, playback state and modes, favourite, sleep timer, speed and ReplayGain, the now-playing
 * artwork seed, and the panel Now Playing's bar has open, which survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playlistRepository: PlaylistRepository,
    private val sleepTimer: SleepTimer,
    private val sleepTimerPreference: SleepTimerPreference,
    private val replayGainPreference: ReplayGainPreference,
    private val seedSource: ArtworkSeedSource,
    castAvailability: CastAvailability,
    savedNowPlaying: SavedNowPlaying,
    private val clearQueue: ClearQueue,
    private val restoreQueue: RestoreQueue,
    private val availableMediaActions: AvailableMediaActions,
    private val mediaActionHandler: MediaActionHandler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(),
    PlayerActions {
    private val castAvailable = castAvailability.isAvailable()

    // Shown on a cold start until the saved queue is restored, then the restored queue's own song takes over.
    private val savedSnapshot: NowPlayingSnapshot? = savedNowPlaying.snapshot()
    private val savedSong: PlayerSong? = savedSnapshot?.toPlayerSong()

    /** Bumped when the sleep timer is started or stopped here. */
    private val sleepTimerChanges = MutableStateFlow(0)
    private val sleepTimerPlayToEnd = MutableStateFlow(sleepTimerPreference.playToEnd)

    private val _events = MutableSharedFlow<PlayerUiEvent>()
    val events: SharedFlow<PlayerUiEvent> = _events.asSharedFlow()

    private var clearedQueue: QueueSnapshot? = null

    private val panel: StateFlow<NowPlayingPanel?> = savedStateHandle.getStateFlow(PANEL_KEY, null)

    private var removedItem: RemovedQueueItem? = null

    private val currentSong: Flow<Song?> = queueOperations.queueStateFlow.map { it.currentItem?.song }.distinctUntilChanged()

    private val favouriteIds: Flow<Set<Long>> =
        flow { emit(playlistRepository.getFavoritesPlaylist()) }
            .flatMapLatest { playlistRepository.getSongsForPlaylist(it) }
            .map { songs -> songs.map { it.song.id }.toSet() }
            .onStart { emit(emptySet<Long>()) }
            .catch { emit(emptySet<Long>()) }

    private val seed: Flow<ArtworkSeed> =
        currentSong
            .map { it?.let(::ArtworkKey) }
            .distinctUntilChanged()
            .mapLatest { key -> key?.let { seedSource.seedFor(it.song) } ?: ArtworkSeed.None }

    // Ticks only while a timer runs, so it notices the timer going off (the timer has no flow of its own).
    private val sleepTimerActive: Flow<Boolean> =
        sleepTimerChanges
            .flatMapLatest {
                if (sleepTimer.timeRemaining() == null) {
                    flowOf(false)
                } else {
                    flow {
                        while (sleepTimer.timeRemaining() != null) {
                            emit(true)
                            delay(SLEEP_TIMER_TICK_MS)
                        }
                        emit(false)
                    }
                }
            }.distinctUntilChanged()

    private val sound: Flow<Sound> =
        combine(playbackOperations.positionAnchorFlow.map { it.speed }.distinctUntilChanged(), replayGainPreference.mode, ::Sound)

    private val extras: Flow<Extras> =
        combine(favouriteIds, seed.onStart { emit(ArtworkSeed.Loading) }, sleepTimerActive, sleepTimerPlayToEnd, sound) { favourites, seed, sleeping, playToEnd, sound ->
            Extras(favourites, seed, sleeping, playToEnd, sound)
        }

    val uiState: StateFlow<PlayerUiState> =
        combine(
            queueOperations.queueStateFlow,
            playbackOperations.playbackStateFlow,
            queueOperations.shuffleModeFlow,
            queueOperations.repeatModeFlow,
            extras,
        ) { queue, playback, shuffle, repeat, extras ->
            queue.toPlayerUiState(savedSong).copy(
                playing = playback == PlaybackState.Playing,
                buffering = playback == PlaybackState.Loading,
                shuffle = shuffle == QueueManager.ShuffleMode.On,
                repeatMode = repeat.toS2RepeatMode(),
                favourite = queue.currentItem?.song?.id?.let { it in extras.favouriteIds } ?: false,
                sleepTimerActive = extras.sleepTimerActive,
                sleepTimerPlayToEnd = extras.sleepTimerPlayToEnd,
                castAvailable = castAvailable,
                seed = extras.seed,
                playbackSpeed = extras.sound.speed,
                replayGainMode = extras.sound.replayGainMode,
            )
        }.combine(panel) { state, panel ->
            // An emptied queue takes the player, and its panel, away.
            state.copy(panel = panel.takeIf { state.hasQueue == true })
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), queueOperations.queueStateFlow.value.toPlayerUiState(savedSong))

    // The total is the song's own duration, as the queue rows show it; the player's reported duration can differ by a rounding second.
    val progress: StateFlow<PlayerProgress> =
        combine(playbackOperations.progressFlow, currentSong) { progress, song ->
            val songDuration = song?.duration?.toLong()?.takeIf { it > 0 }
            when {
                progress != null -> PlayerProgress(progress.position.toLong(), songDuration ?: progress.duration.toLong())
                song != null -> PlayerProgress(song.playbackPosition.toLong(), song.duration.toLong())
                else -> savedProgress() ?: PlayerProgress.Zero
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedProgress() ?: PlayerProgress.Zero)

    /** Where the saved song was left, while it stands in for a queue not yet restored. */
    private fun savedProgress(): PlayerProgress? = savedSnapshot
        ?.takeIf { queueOperations.queueStateFlow.value.isAwaitingRestore }
        ?.let { snapshot -> PlayerProgress(snapshot.positionMs.toLong(), snapshot.durationMs.toLong()) }

    override fun togglePlayback() {
        if (savedSnapshot != null && queueOperations.queueStateFlow.value.isAwaitingRestore) {
            // Nothing is loaded yet: play once the saved queue is restored and loaded.
            viewModelScope.launch {
                queueOperations.queueStateFlow.first { queue -> queue.isRestored }
                playbackOperations.play()
            }
        } else {
            playbackOperations.togglePlayback()
        }
    }

    override fun skipToNext() = playbackOperations.skipToNext(ignoreRepeat = true)

    override fun skipToPrevious() = playbackOperations.skipToPrev()

    override fun seekTo(positionMs: Long) = playbackOperations.seekTo(positionMs.toInt())

    override fun toggleShuffle() {
        viewModelScope.launch { queueOperations.toggleShuffleMode() }
    }

    override fun cycleRepeatMode() = queueOperations.toggleRepeatMode()

    override fun toggleFavourite() {
        val song = queueOperations.getCurrentItem()?.song ?: return
        val favourite = uiState.value.favourite
        viewModelScope.launch {
            val favourites = playlistRepository.getFavoritesPlaylist()
            if (favourite) {
                playlistRepository.removeSongsFromPlaylist(favourites, listOf(song))
            } else {
                playlistRepository.addToPlaylist(favourites, listOf(song))
            }
        }
    }

    override fun startSleepTimer(
        durationMs: Long,
        playToEnd: Boolean,
    ) {
        sleepTimerPreference.playToEnd = playToEnd
        sleepTimerPlayToEnd.value = playToEnd
        sleepTimer.startTimer(durationMs, playToEnd)
        sleepTimerChanges.value++
    }

    override fun stopSleepTimer() {
        sleepTimer.stopTimer()
        sleepTimerChanges.value++
    }

    override fun sleepTimerRemaining(): Flow<Long?> = flow {
        while (true) {
            emit(sleepTimer.timeRemaining())
            delay(SLEEP_TIMER_TICK_MS)
        }
    }.distinctUntilChanged()

    override fun setPlaybackSpeed(speed: Float) = playbackOperations.setPlaybackSpeed(speed)

    override fun setReplayGainMode(mode: ReplayGainMode) = replayGainPreference.set(mode)

    override fun togglePanel(panel: NowPlayingPanel) = showPanel(panel.takeUnless { it == this.panel.value })

    override fun showPanel(panel: NowPlayingPanel?) {
        savedStateHandle[PANEL_KEY] = panel
    }

    override fun skipToQueueItem(uid: Long) {
        val index = queueOperations.getQueue().indexOfFirst { it.uid == uid }
        if (index >= 0) playbackOperations.skipTo(index)
    }

    override fun moveQueueItem(
        uid: Long,
        afterUid: Long?,
    ) {
        queueMove(queueOperations.getQueue().map { it.uid }, uid, afterUid)?.let { (from, to) -> playbackOperations.moveQueueItem(from, to) }
    }

    override fun removeQueueItem(uid: Long) {
        val queue = queueOperations.getQueue()
        val index = queue.indexOfFirst { it.uid == uid }
        if (index < 0) return
        playbackOperations.removeQueueItem(queue[index])
        removedItem = RemovedQueueItem(queue[index].song, index)
        viewModelScope.launch { _events.emit(PlayerUiEvent.QueueItemRemoved) }
    }

    // Back in as a new row at the end, then moved to where the old row was.
    override fun undoRemoveQueueItem() {
        val removed = removedItem ?: return
        removedItem = null
        viewModelScope.launch {
            playbackOperations.addToQueue(listOf(removed.song))
            val last = queueOperations.getSize() - 1
            if (removed.index < last) playbackOperations.moveQueueItem(last, removed.index)
        }
    }

    override fun playNext(uid: Long) {
        val from = queueOperations.getQueue().indexOfFirst { it.uid == uid }
        val current = queueOperations.getCurrentPosition() ?: return
        if (from < 0 || from == current) return
        playbackOperations.moveQueueItem(from, if (from < current) current else current + 1)
    }

    override fun clearQueue() {
        val snapshot = clearQueue.invoke() ?: return
        clearedQueue = snapshot
        viewModelScope.launch { _events.emit(PlayerUiEvent.QueueCleared(snapshot.songs.size)) }
    }

    override fun undoClearQueue() {
        val snapshot = clearedQueue ?: return
        clearedQueue = null
        viewModelScope.launch { restoreQueue(snapshot) }
    }

    override fun songActions(song: Song): Flow<List<MediaActionType>> = availableMediaActions(MediaSelection.Songs(song)).map { types -> types.filter { it in PlayerSongActions } }

    override fun playlists(): Flow<List<Playlist>> = playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null))

    override fun onMediaAction(action: MediaAction) {
        viewModelScope.launch { _events.emit(PlayerUiEvent.MediaActionDone(mediaActionHandler.handle(action))) }
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
        val sound: Sound,
    )

    private data class Sound(
        val speed: Float,
        val replayGainMode: ReplayGainMode,
    )

    /** Songs on one album share artwork, so they share a seed. */
    private class ArtworkKey(
        val song: Song,
    ) {
        private val key = song.albumGroupKey

        override fun equals(other: Any?) = other is ArtworkKey && other.key == key

        override fun hashCode() = key.hashCode()
    }

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

/**
 * The from and to indices that put [uid] just after [afterUid] (at the top when null) in the live
 * queue [uids], or null when either row has gone or the row is already there.
 */
internal fun queueMove(
    uids: List<Long>,
    uid: Long,
    afterUid: Long?,
): Pair<Int, Int>? {
    val from = uids.indexOf(uid).takeIf { it >= 0 } ?: return null
    val to = if (afterUid == null) {
        0
    } else {
        val anchor = uids.indexOf(afterUid).takeIf { it >= 0 } ?: return null
        if (anchor < from) anchor + 1 else anchor
    }
    return (from to to).takeIf { from != to }
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

private fun QueueManager.RepeatMode.toS2RepeatMode(): S2RepeatMode = when (this) {
    QueueManager.RepeatMode.Off -> S2RepeatMode.Off
    QueueManager.RepeatMode.All -> S2RepeatMode.All
    QueueManager.RepeatMode.One -> S2RepeatMode.One
}
