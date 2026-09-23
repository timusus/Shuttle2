package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Builds a [PlaybackManager] wired with fakes, defaulting every collaborator so a test only needs
 * to override the ones it cares about.
 */
fun testPlaybackManager(
    exoplayerPlayback: Playback = FakePlayback("default"),
    queueWatcher: QueueWatcher = QueueWatcher(),
    queueManager: QueueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences())),
    playbackWatcher: PlaybackWatcher = PlaybackWatcher(),
    audioFocusHelper: AudioFocusHelper = FakeAudioFocusHelper(),
    playbackPreferenceManager: PlaybackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build()),
    audioEffectSessionManager: AudioEffectSessionManager = AudioEffectSessionManager(openSession = {}, closeSession = {}),
    appCoroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
): PlaybackManager = PlaybackManager(
    queueManager = queueManager,
    playbackWatcher = playbackWatcher,
    audioFocusHelper = audioFocusHelper,
    playbackPreferenceManager = playbackPreferenceManager,
    audioEffectSessionManager = audioEffectSessionManager,
    appCoroutineScope = appCoroutineScope,
    exoplayerPlayback = exoplayerPlayback,
    queueWatcher = queueWatcher,
    audioManager = null
)
