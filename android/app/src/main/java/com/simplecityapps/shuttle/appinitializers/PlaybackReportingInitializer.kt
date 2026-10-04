package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.playbackreporting.PlaybackReporting
import dev.zacsweers.metro.Inject

/** Starts [PlaybackReporting], the playback reporting both platforms share (#96, #773). */
class PlaybackReportingInitializer
@Inject
constructor(
    private val playbackReporting: PlaybackReporting
) : AppInitializer {
    override fun init(application: Application) {
        playbackReporting.start()
    }
}
