package com.simplecityapps.playback

import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

/**
 * Owns which [Playback] is active, and moves playback from one to another (e.g. local <-> Cast).
 *
 * A switch carries the old playback's settings over (repeat mode, audio session id, playback speed),
 * detaches, pauses and releases the old playback, points audio focus and the audio effect session at
 * the new one, then loads the current item into it at the current position. Once that load
 * succeeds, the effect session is rebound to whatever session the loaded player actually rendered on,
 * the saved position is restored, and playback resumes if the old playback was playing and the new
 * one wants to resume after a switch.
 *
 * Each switch supersedes the one before it: a switch's completion only acts if no later switch has
 * happened, so a fast local -> Cast -> local toggle can't rebind, seek or play on the wrong playback
 * even if the superseded load reports late. [load] is expected to deliver only the latest load's
 * completion as well (see [LoadCoordinator]); the generation guard here doesn't rely on it.
 *
 * Not thread-safe: every call and every load completion must arrive on the main thread.
 */
class PlaybackSwitcher(
    initialPlayback: Playback,
    /** Receives the active playback's reports. A playback switched away from is detached from it. */
    private val callback: Playback.Callback,
    /** The audio session every playback is asked to render on. */
    private val audioSessionId: Int,
    private val audioFocusHelper: AudioFocusHelper,
    private val audioEffectSessionManager: AudioEffectSessionManager,
    private val repeatMode: () -> QueueManager.RepeatMode,
    /**
     * The position a switch loads the new playback at. While a load is pending this is the load's
     * position, not the old playback's, which still reports the item being replaced.
     */
    private val currentProgress: () -> Int?,
    /** The position to restore once a switch has loaded, or null to stay where the load started. */
    private val savedPosition: () -> Int?,
    /** Called once the new playback is active and attached, before its load starts. */
    private val onSwitched: () -> Unit,
    /** Loads the current item into the active playback at the given position. */
    private val load: (seekPosition: Int, completion: (Result<Boolean>) -> Unit) -> Unit,
    private val seekTo: (position: Int) -> Unit,
    private val play: () -> Unit
) {
    /** The active playback. */
    var playback: Playback = initialPlayback
        private set

    /** Incremented on every switch, so a switch's completion can tell a later one has happened. */
    private var generation = 0L

    /**
     * Attaches the initial playback and opens the effect session on [audioSessionId]. Call once, when
     * [callback] is ready to receive reports.
     */
    fun attachInitialPlayback() {
        attach(playback)
        audioFocusHelper.enabled = playback.respondsToAudioFocus()
        audioEffectSessionManager.bindTo(audioSessionId)
    }

    fun switchTo(newPlayback: Playback) {
        Timber.v("switchToPlayback(playback: ${newPlayback.javaClass.simpleName})")

        val generation = ++this.generation

        val oldPlayback = playback
        val wasPlaying = oldPlayback.playBackState() is PlaybackState.Playing
        val seekPosition = currentProgress()
        val playbackSpeed = oldPlayback.getPlaybackSpeed()

        oldPlayback.pause()
        oldPlayback.release()
        // A released playback can still report (e.g. a load it started before the switch), which
        // would overwrite the new playback's state.
        oldPlayback.callback = null

        playback = newPlayback
        attach(newPlayback)
        newPlayback.setPlaybackSpeed(playbackSpeed)
        audioFocusHelper.enabled = newPlayback.respondsToAudioFocus()
        rebindAudioEffectSession(newPlayback)
        onSwitched()

        load(seekPosition ?: 0) { result ->
            if (generation != this.generation) {
                Timber.v("Switch $generation completed after being superseded by switch ${this.generation}; ignoring")
                return@load
            }
            result.onSuccess {
                rebindAudioEffectSession(newPlayback)
                savedPosition()?.let(seekTo)
                if (wasPlaying && newPlayback.getResumeWhenSwitched(oldPlayback)) {
                    play()
                }
            }
        }
    }

    private fun attach(playback: Playback) {
        playback.setRepeatMode(repeatMode())
        playback.callback = callback
        playback.setAudioSessionId(audioSessionId)
    }

    /**
     * Moves the audio effect control session to whatever session [playback] is actually rendering
     * on. Called on switch, so a playback with no local audio session (Chromecast) closes the
     * session rather than leaving system and OEM effects bound to a now-silent one, and again once
     * loaded, in case the player couldn't honour the id we asked for. The first call closes the
     * old session straight away, even if the load later fails; the second is the only one that sees
     * the id the loaded player actually rendered on, so both are needed.
     */
    private fun rebindAudioEffectSession(playback: Playback) {
        audioEffectSessionManager.bindTo(playback.getAudioSessionId())
    }
}
