package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.BitPerfectOutput
import com.simplecityapps.playback.CastStarter
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Starts playback when the app is launched: the playback components that run for the life of the app (Cast, once the
 * app first comes to the foreground, the media session and bit-perfect USB output), and the restore of the saved queue
 * ([QueueStore], which saves it too). Starts [PlaybackService] when playback starts.
 *
 * Track ends and pauses are events, collected from [PlaybackOperations.trackEndedFlow] and
 * [PlaybackOperations.pausePositionFlow] to record each song's own position and play count.
 */
class PlaybackInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playbackOperations: PlaybackOperations,
    private val queueStore: QueueStore,
    private val castStarter: Lazy<CastStarter>,
    private val playRequests: Lazy<PlayRequests>,
    private val bitPerfectOutput: Lazy<BitPerfectOutput>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        Timber.v("PlaybackInitializer.init()")

        startPlaybackComponents(application)
        collectPlaybackState()
        collectSongPositions()

        // A saved song that can't load (a server out of reach, a file not there yet) stays where it was left.
        queueStore.restore { positionMs -> playbackOperations.load(positionMs, skipUnloadable = false) {} }
    }

    /** Each starts itself when it's created, so creating it here is what starts it; Cast waits for the foreground. */
    private fun startPlaybackComponents(application: Application) {
        castStarter.get().startInForeground(application)
        playRequests.get().launchPlaybackFailureMessages()
        bitPerfectOutput.get()
    }

    /** Compared against a snapshot taken here, so the initial state isn't handled as a change, and none made in between is missed. */
    private fun collectPlaybackState() {
        val playbackState = playbackOperations.playbackStateFlow.value
        appCoroutineScope.launchCollectingChanges(playbackOperations.playbackStateFlow, playbackState, Dispatchers.Main.immediate) { _, current ->
            if (current is PlaybackState.Playing) {
                startPlaybackService()
            }
        }
    }

    private fun collectSongPositions() {
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { song -> recordPlayedThrough(song) }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.pausePositionFlow.collect { songPosition -> saveSongPosition(songPosition) }
        }
    }

    private fun startPlaybackService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_START))
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background - likely audio focus regained while app in background")
            } else {
                throw e
            }
        }
    }

    private fun saveSongPosition(songPosition: SongPosition) {
        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(songPosition.song, songPosition.positionMs)
            }
        }
    }

    private fun recordPlayedThrough(song: Song) {
        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(song, song.duration)
                songRepository.incrementPlayCount(song)
            }
        }
    }
}
