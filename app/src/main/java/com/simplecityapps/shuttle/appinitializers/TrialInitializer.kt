package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

class TrialInitializer @Inject constructor(
    private val trialManager: TrialManager,
    private val playbackManager: PlaybackManager,
    @Named("AppCoroutineScope") private val coroutineScope: CoroutineScope
) : AppInitializer {

    override fun init(application: Application) {
        coroutineScope.launch {
            trialManager.trialState.collect { trialState ->
                when (trialState) {
                    is TrialState.Expired -> {
                        playbackManager.setPlaybackSpeed(trialState.multiplier())
                    }
                    TrialState.Paid -> {
                        playbackManager.setPlaybackSpeed(1.0f)
                    }
                    is TrialState.Trial -> {
                        playbackManager.setPlaybackSpeed(1.0f)
                    }
                    TrialState.Unknown -> {
                        playbackManager.setPlaybackSpeed(1.0f)
                    }
                }
            }
        }

        coroutineScope.launch {
            trialManager.updateTrialState()
        }
    }
}