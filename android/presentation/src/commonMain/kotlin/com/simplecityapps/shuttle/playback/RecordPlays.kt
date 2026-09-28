package com.simplecityapps.shuttle.playback

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.IoDispatcher
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Records each song's plays, for Recently Played, Most Played and an artist's Top Songs, on both platforms: a track
 * end ([PlaybackOperations.trackEndedFlow]) counts the song as played through (its play count, last played and last
 * completed), and a pause ([PlaybackOperations.pausePositionFlow]) saves its position (and when it was last played).
 * Started once at app start, it runs for the life of the app; each write goes to [ioDispatcher].
 */
@Inject
class RecordPlays(
    private val playbackOperations: PlaybackOperations,
    private val songRepository: SongRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /** Both flows are events that replay nothing, so they're collected on main straight away, missing none sent after. */
    fun start() {
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { song ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.recordPlayedThrough(song) }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.pausePositionFlow.collect { songPosition ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.setPlaybackPosition(songPosition.song, songPosition.positionMs) }
            }
        }
    }
}
