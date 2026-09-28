package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.SavedQueueSongs
import com.simplecityapps.playback.persistence.SavedQueueWriter
import com.simplecityapps.playback.persistence.readSavedQueue
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.Preference
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps iOS playback across launches in the prefs Android's `QueueStore` and `PlaybackSpeedStore` use (android/playback),
 * in the same keys and format ([PlaybackPreferenceManager], [SavedQueueWriter]): the queue in both orders, the position
 * in it and the song it names, the shuffle and repeat modes, the position in the current song, and the speed.
 *
 * [start] restores them, then saves them as [controller] changes them. The queue and the modes are saved on every
 * change after the one the app starts with, so the empty queue it starts with never replaces the saved one. The
 * position is saved on every jump and pause, and every [SAVE_INTERVAL_MS] of playback while playing; it's the current
 * song's, so it's cleared when another song becomes current, until one is saved for it (a play of a song that isn't
 * loaded starts it at [PlaybackPreferenceManager.playbackPosition], else its own start).
 *
 * Main thread only: [scope] runs on it (`Dispatchers.Main.immediate` on iOS), as the controller does.
 */
class IosPlaybackStore(
    private val controller: IosPlayerController,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val playbackSpeed: Preference<Float>,
    private val songRepository: SongRepository,
    private val scope: CoroutineScope,
    /** Where the saved queue is read from the library, off the main thread. */
    private val readContext: CoroutineContext = Dispatchers.Default
) {
    private val queueOperations = controller.queueOperations

    private val writer = SavedQueueWriter(playbackPreferenceManager)

    /** The position last saved while playing, to save the next once playback has moved on [SAVE_INTERVAL_MS]. */
    private var savedPosition: Int? = null

    /**
     * Puts the saved speed back straight away, starts saving, and restores the saved modes and queue: the queue is read
     * off the main thread, then set and loaded paused at the position to resume from, unless something set the queue
     * meanwhile. The queue is marked restored once that's done, or failed, so `hasQueue` settles either way.
     */
    fun start() {
        restoreSpeed()
        startSaving()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                restore()
            } finally {
                queueOperations.hasRestoredQueue = true
            }
        }
    }

    // The restore

    /** Sets the saved speed, unless it's normal speed. */
    private fun restoreSpeed() {
        playbackSpeed.value.takeIf { it > 0f && it != 1f }?.let(controller::setPlaybackSpeed)
    }

    private suspend fun restore() {
        val shuffleMode = playbackPreferenceManager.shuffleMode
        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition
        queueOperations.setShuffleMode(shuffleMode, reshuffle = false)
        queueOperations.setRepeatMode(playbackPreferenceManager.repeatMode)
        val initialContentVersion = queueOperations.queueStateFlow.value.contentVersion

        val saved = queuePosition?.let { position -> readSavedQueue(shuffleMode, position) }

        if (saved != null) {
            if (!controller.restoreQueue(initialContentVersion, saved.songs, saved.shuffleSongs, saved.position, shuffleMode, playbackPreferenceManager.playContext)) return
        } else if (queueOperations.queueStateFlow.value.contentVersion != initialContentVersion) {
            return
        }
        if (queueOperations.queueStateFlow.value.items.isEmpty()) {
            // Nothing to show for a queue that's gone.
            playbackPreferenceManager.nowPlaying = null
            return
        }
        val restoredSeekPosition = if (saved?.fromStart == true) 0 else seekPosition
        if (restoredSeekPosition != seekPosition) {
            // It's what a play of the unloaded song reads back as the position to resume from.
            playbackPreferenceManager.playbackPosition = restoredSeekPosition
        }
        // A saved song that can't load (a server out of reach) stays where it was left, as on Android.
        controller.load(restoredSeekPosition, skipUnloadable = false) {}
    }

    /**
     * The saved queue, read from the library off the main thread; null if there's none or none of its songs are left. A
     * failed read goes to the scope's exception handler, and [start] still marks the queue restored.
     */
    private suspend fun readSavedQueue(
        shuffleMode: ShuffleMode,
        queuePosition: Int
    ): SavedQueueSongs? = withContext(readContext) {
        playbackPreferenceManager.readSavedQueue(shuffleMode, queuePosition) { songIds ->
            songRepository.loadSongs(SongQuery.SongIds(songIds)).associateBy { song -> song.id }
        }
    }

    // Saving

    private fun startSaving() {
        scope.launchCollectingChanges(queueOperations.shuffleModeFlow, queueOperations.shuffleModeFlow.value) { _, shuffleMode ->
            playbackPreferenceManager.shuffleMode = shuffleMode
        }
        scope.launchCollectingChanges(queueOperations.repeatModeFlow, queueOperations.repeatModeFlow.value) { _, repeatMode ->
            playbackPreferenceManager.repeatMode = repeatMode
        }
        scope.launchCollectingChanges(queueOperations.queueStateFlow, queueOperations.queueStateFlow.value, onChange = ::saveQueue)
        scope.launchCollectingChanges(controller.progressFlow, controller.progressFlow.value) { _, _ -> savePosition(throttled = true) }
        scope.launchCollectingChanges(controller.playbackStateFlow, controller.playbackStateFlow.value) { _, _ -> savePosition(throttled = false) }
        scope.launchCollectingChanges(controller.playbackSpeedFlow, controller.playbackSpeedFlow.value) { _, speed ->
            playbackSpeed.value = speed
        }
    }

    private fun saveQueue(
        previous: QueueState,
        current: QueueState
    ) {
        if (current.contentVersion != previous.contentVersion || current.songDataVersion != previous.songDataVersion) {
            writer.saveQueue(
                songs = queueOperations.getQueue(ShuffleMode.Off).map { item -> item.song },
                shuffleSongs = queueOperations.getQueue(ShuffleMode.On).map { item -> item.song }
            )
            // The queue's context changes only with the queue.
            playbackPreferenceManager.playContext = queueOperations.playContext
        }
        writer.savePosition(current.items.map { item -> item.song }, current.currentPosition)
        // Another song became current (not the first of a queue set on an empty one, whose position is a restore's
        // own): it resumes from its own start until a position is saved for it.
        if (current.currentItem?.uid != previous.currentItem?.uid && previous.currentItem != null) {
            playbackPreferenceManager.playbackPosition = null
            savedPosition = null
        }
    }

    /**
     * Saves where playback is in the current song. [throttled] (a progress tick) saves only once playback has moved
     * [SAVE_INTERVAL_MS] from the position last saved while playing; a jump that far, or any change while not playing,
     * is saved straight away.
     */
    private fun savePosition(throttled: Boolean) {
        if (queueOperations.getCurrentItem() == null) return
        val position = controller.getProgress() ?: return
        val last = savedPosition
        if (throttled && controller.playbackState() == PlaybackState.Playing && last != null && abs(position - last) < SAVE_INTERVAL_MS) return
        savedPosition = position
        playbackPreferenceManager.playbackPosition = position
    }

    companion object {
        /** How far playback moves between the saves made while playing. */
        const val SAVE_INTERVAL_MS = 1_000
    }
}
