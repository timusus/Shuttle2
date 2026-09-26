package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** A cleared queue as it stood, in both orders, so [RestoreQueue] can put it back. */
data class QueueSnapshot(
    val songs: List<Song>,
    /** The shuffled order when shuffle was on, else null. */
    val shuffleSongs: List<Song>?,
    /** The current item's index in the order that was playing. */
    val position: Int,
    val seekPositionMs: Int?,
    val playing: Boolean,
)

/** Clears the queue, returning what it held, or null when it was already empty. */
class ClearQueue @Inject constructor(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    operator fun invoke(): QueueSnapshot? {
        if (queueOperations.getSize() == 0) return null
        val shuffled = queueOperations.getShuffleMode() == ShuffleMode.On
        val snapshot = QueueSnapshot(
            songs = queueOperations.getQueue(ShuffleMode.Off).map { it.song },
            shuffleSongs = if (shuffled) queueOperations.getQueue(ShuffleMode.On).map { it.song } else null,
            position = queueOperations.getCurrentPosition() ?: 0,
            seekPositionMs = playbackOperations.getProgress(),
            playing = playbackOperations.playbackState() == PlaybackState.Playing,
        )
        playbackOperations.clearQueue()
        return snapshot
    }
}

/** Puts a cleared queue back where it was, playing again if it was playing. */
class RestoreQueue @Inject constructor(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    suspend operator fun invoke(snapshot: QueueSnapshot) {
        if (!queueOperations.setQueue(snapshot.songs, snapshot.shuffleSongs, snapshot.position)) return
        // A restored song that can't load stays where it was left, rather than the queue moving on (RS-56).
        playbackOperations.load(snapshot.seekPositionMs, skipUnloadable = false) { result ->
            if (snapshot.playing && result.isSuccess) playbackOperations.play()
        }
    }
}
