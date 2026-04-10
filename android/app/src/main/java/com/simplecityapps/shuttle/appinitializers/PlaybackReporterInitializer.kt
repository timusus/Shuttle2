package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.shuttle.playback.PlaybackReporter
import javax.inject.Inject
import timber.log.Timber

/**
 * Initializes the PlaybackReporter by registering it with the PlaybackWatcher.
 *
 * This allows the PlaybackReporter to listen for playback events and report them
 * to remote media servers (Jellyfin, Emby, Plex).
 */
class PlaybackReporterInitializer
@Inject
constructor(
    private val playbackWatcher: PlaybackWatcher,
    private val playbackReporter: PlaybackReporter
) : AppInitializer {
    override fun init(application: Application) {
        Timber.v("PlaybackReporterInitializer.init()")
        playbackWatcher.addCallback(playbackReporter)
    }
}
