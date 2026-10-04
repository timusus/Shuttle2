package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.scrobbling.PlaybackScrobbling
import dev.zacsweers.metro.Inject

/** Starts [PlaybackScrobbling] (#503) at launch; the wiring itself is shared with iOS. */
class ScrobblingInitializer
@Inject
constructor(
    private val playbackScrobbling: PlaybackScrobbling
) : AppInitializer {
    override fun init(application: Application) {
        playbackScrobbling.start()
    }
}
