package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.ProgressTicker
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Builds a [PlaybackManager] wired with fakes, defaulting every collaborator so a test only needs
 * to override the ones it cares about.
 */
fun testPlaybackManager(
    exoplayerPlayback: Playback = FakePlayback("default"),
    queueManager: QueueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences())),
    audioFocusHelper: AudioFocusHelper = FakeAudioFocusHelper(),
    playbackPreferenceManager: PlaybackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build()),
    audioEffectSessionManager: AudioEffectSessionManager = AudioEffectSessionManager(openSession = {}, closeSession = {}),
    // Runs inline like Dispatchers.Unconfined, but on virtual time, so a load's timeout only fires when
    // a test advances the dispatcher's scheduler.
    appCoroutineScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    // A dispatcher nothing advances, so progress never ticks unless a test supplies its own ticker.
    progressTicker: ProgressTicker = ProgressTicker(CoroutineScope(StandardTestDispatcher())),
    elapsedRealtime: () -> Long = { 0L },
    // Queue changes are handled inline, as they are on the main thread in production.
    queueChangeContext: CoroutineContext = Dispatchers.Unconfined
): PlaybackManager = PlaybackManager(
    queueManager = queueManager,
    audioFocusHelper = audioFocusHelper,
    playbackPreferenceManager = playbackPreferenceManager,
    audioEffectSessionManager = audioEffectSessionManager,
    appCoroutineScope = appCoroutineScope,
    progressTicker = progressTicker,
    exoplayerPlayback = exoplayerPlayback,
    audioManager = null,
    elapsedRealtime = elapsedRealtime,
    queueChangeContext = queueChangeContext
)
