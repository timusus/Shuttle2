package com.simplecityapps.shuttle.ui.shell.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
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

/** The sleep timer's remembered "play last song to end" choice. */
interface SleepTimerPreference {
    var playToEnd: Boolean
}

/**
 * The player surfaces' state and actions (docs/architecture/app-shell.md, sections 1 and 5): the
 * queue, playback state and modes, favourite, sleep timer and the now-playing artwork seed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playlistRepository: PlaylistRepository,
    private val sleepTimer: SleepTimer,
    private val sleepTimerPreference: SleepTimerPreference,
    private val seedSource: ArtworkSeedSource,
    castAvailability: CastAvailability,
    private val clearQueue: ClearQueue,
    private val restoreQueue: RestoreQueue,
) : ViewModel(),
    PlayerActions {
    private val castAvailable = castAvailability.isAvailable()

    /** Bumped when the sleep timer is started or stopped here. */
    private val sleepTimerChanges = MutableStateFlow(0)
    private val sleepTimerPlayToEnd = MutableStateFlow(sleepTimerPreference.playToEnd)

    private val _events = MutableSharedFlow<PlayerUiEvent>()
    val events: SharedFlow<PlayerUiEvent> = _events.asSharedFlow()

    private var clearedQueue: QueueSnapshot? = null

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

    private val extras: Flow<Extras> =
        combine(favouriteIds, seed.onStart { emit(ArtworkSeed.Loading) }, sleepTimerActive, sleepTimerPlayToEnd) { favourites, seed, sleeping, playToEnd ->
            Extras(favourites, seed, sleeping, playToEnd)
        }

    val uiState: StateFlow<PlayerUiState> =
        combine(
            queueOperations.queueStateFlow,
            playbackOperations.playbackStateFlow,
            queueOperations.shuffleModeFlow,
            queueOperations.repeatModeFlow,
            extras,
        ) { queue, playback, shuffle, repeat, extras ->
            queue.toPlayerUiState().copy(
                playing = playback == PlaybackState.Playing,
                buffering = playback == PlaybackState.Loading,
                shuffle = shuffle == QueueManager.ShuffleMode.On,
                repeatMode = repeat.toS2RepeatMode(),
                favourite = queue.currentItem?.song?.id?.let { it in extras.favouriteIds } ?: false,
                sleepTimerActive = extras.sleepTimerActive,
                sleepTimerPlayToEnd = extras.sleepTimerPlayToEnd,
                castAvailable = castAvailable,
                seed = extras.seed,
            )
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), queueOperations.queueStateFlow.value.toPlayerUiState())

    // The total is the song's own duration, as the queue rows show it; the player's reported duration can differ by a rounding second.
    val progress: StateFlow<PlayerProgress> =
        combine(playbackOperations.progressFlow, currentSong) { progress, song ->
            val songDuration = song?.duration?.toLong()?.takeIf { it > 0 }
            when {
                progress != null -> PlayerProgress(progress.position.toLong(), songDuration ?: progress.duration.toLong())
                song != null -> PlayerProgress(song.playbackPosition.toLong(), song.duration.toLong())
                else -> PlayerProgress.Zero
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerProgress.Zero)

    override fun togglePlayback() = playbackOperations.togglePlayback()

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

    override fun skipToQueueItem(uid: Long) {
        val index = queueOperations.getQueue().indexOfFirst { it.uid == uid }
        if (index >= 0) playbackOperations.skipTo(index)
    }

    override fun moveQueueItem(
        from: Int,
        to: Int,
    ) {
        if (from != to) playbackOperations.moveQueueItem(from, to)
    }

    override fun removeQueueItem(uid: Long) {
        queueOperations.getQueue().firstOrNull { it.uid == uid }?.let(playbackOperations::removeQueueItem)
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

    private data class Extras(
        val favouriteIds: Set<Long>,
        val seed: ArtworkSeed,
        val sleepTimerActive: Boolean,
        val sleepTimerPlayToEnd: Boolean,
    )

    /** Songs on one album share artwork, so they share a seed. */
    private class ArtworkKey(
        val song: Song,
    ) {
        private val key = song.albumGroupKey

        override fun equals(other: Any?) = other is ArtworkKey && other.key == key

        override fun hashCode() = key.hashCode()
    }

    private companion object {
        const val SLEEP_TIMER_TICK_MS = 1_000L
    }
}

internal fun QueueState.toPlayerUiState(): PlayerUiState {
    if (items.isEmpty() && !isRestored) return PlayerUiState.Unknown
    val currentIndex = currentPosition ?: -1
    val rows = items.mapIndexed { index, item ->
        PlayerSong(
            uid = item.uid,
            title = item.song.name.orEmpty(),
            artist = item.song.friendlyArtistName ?: item.song.albumArtist,
            album = item.song.album,
            durationMs = item.song.duration,
            position = when {
                item.isCurrent -> QueuePosition.Current
                index < currentIndex -> QueuePosition.Played
                else -> QueuePosition.Upcoming
            },
            song = item.song,
        )
    }
    return PlayerUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull { it.position == QueuePosition.Current }, items = rows)
}

private fun QueueManager.RepeatMode.toS2RepeatMode(): S2RepeatMode = when (this) {
    QueueManager.RepeatMode.Off -> S2RepeatMode.Off
    QueueManager.RepeatMode.All -> S2RepeatMode.All
    QueueManager.RepeatMode.One -> S2RepeatMode.One
}
