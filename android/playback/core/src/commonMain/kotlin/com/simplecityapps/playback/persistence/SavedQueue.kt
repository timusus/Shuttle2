package com.simplecityapps.playback.persistence

import com.simplecityapps.playback.PlaybackPolicy
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Song

/**
 * The saved queue's format and rules, shared by Android's `QueueStore` (android/playback), which saves from the Media3
 * playlist, and iOS's `IosPlaybackStore` (:shared), which saves from its queue model: the queue in both orders as
 * comma separated song ids, the position in the order the shuffle mode presents, and the song it names. Songs that
 * aren't in the library (files opened from other apps) are left out: there'd be nothing to restore them from.
 */
class SavedQueueWriter(
    private val playbackPreferenceManager: PlaybackPreferenceManager
) {
    /** What [saveNowPlaying] saved last, to leave an unchanged one alone. */
    private var savedNowPlaying: NowPlayingSnapshot? = null

    /** Saves the queue in queue order ([songs]) and shuffled order ([shuffleSongs]). A queue that's what's saved already (the one a restore just set) is left alone. */
    fun saveQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>
    ) {
        val queueIds = songs.libraryIds()
        if (queueIds != playbackPreferenceManager.queueIds) playbackPreferenceManager.queueIds = queueIds
        val shuffleQueueIds = shuffleSongs.libraryIds()
        if (shuffleQueueIds != playbackPreferenceManager.shuffleQueueIds) playbackPreferenceManager.shuffleQueueIds = shuffleQueueIds
    }

    /**
     * Saves [currentPosition] in [songs] (the queue as the shuffle mode presents it) as a position in the saved queue,
     * and the song it names. Which one that is depends on the songs before it too, once some are left out.
     */
    fun savePosition(
        songs: List<Song>,
        currentPosition: Int?
    ) {
        val savedPosition = savedQueuePosition(songs, currentPosition)
        playbackPreferenceManager.queuePosition = savedPosition?.position
        playbackPreferenceManager.restoreQueuePositionFromStart = savedPosition?.fromStart ?: false
        saveNowPlaying(savedPosition?.let { songs.filter { song -> song.isInLibrary }[it.position] })
    }

    /** Saves [song] as the one the saved position names, unless it's what was saved last. */
    private fun saveNowPlaying(song: Song?) {
        val snapshot = song?.let(NowPlayingSnapshot::of)
        if (snapshot != savedNowPlaying) {
            savedNowPlaying = snapshot
            playbackPreferenceManager.nowPlaying = snapshot
        }
    }

    private fun List<Song>.libraryIds(): String? = filter { song -> song.isInLibrary }.joinToString(",") { song -> song.id.toString() }.ifEmpty { null }
}

/**
 * The saved queue, read back from the library.
 *
 * @param songs the queue in queue order.
 * @param shuffleSongs the saved shuffled order of the same songs, if there is one.
 * @param position the position to restore, in [shuffleSongs] when the saved shuffle mode is on and there is one, else in [songs].
 * @param fromStart true when the song at [position] plays from the beginning rather than the saved playback position.
 */
class SavedQueueSongs(
    val songs: List<Song>,
    val shuffleSongs: List<Song>?,
    val position: Int,
    val fromStart: Boolean
)

/**
 * Reads the saved queue saved with [shuffleMode] at [queuePosition], looking its songs up with [songsById]. A song
 * gone from the library since the queue was saved is dropped, so the position is found again among the songs that are
 * left, in the list the shuffle mode presents. Null if there's no saved queue, or none of its songs are left.
 */
suspend fun PlaybackPreferenceManager.readSavedQueue(
    shuffleMode: ShuffleMode,
    queuePosition: Int,
    songsById: suspend (songIds: List<Long>) -> Map<Long, Song>
): SavedQueueSongs? {
    val songIds = queueIds.toSongIds().orEmpty()
    if (songIds.isEmpty()) return null
    val shuffleSongIds = shuffleQueueIds.toSongIds()

    val found = songsById((songIds + shuffleSongIds.orEmpty()).distinct())

    val songs = songIds.mapNotNull { songId -> found[songId] }
    val shuffleSongs = shuffleSongIds?.mapNotNull { songId -> found[songId] }
    val positionIds = if (shuffleMode == ShuffleMode.On && shuffleSongIds != null) shuffleSongIds else songIds
    val restoredPosition = restoredQueuePosition(positionIds, queuePosition, found.keys) ?: return null

    return SavedQueueSongs(
        songs = songs,
        shuffleSongs = shuffleSongs,
        position = restoredPosition.position,
        fromStart = restoredPosition.fromStart || restoreQueuePositionFromStart
    )
}

/** Where to resume [song] from: the saved position, else where the song itself says to start ([PlaybackPolicy.startOf]). */
fun PlaybackPreferenceManager.resumePosition(song: Song?): Int = playbackPosition ?: song?.let(PlaybackPolicy::startOf) ?: 0

/**
 * A queue position to save or restore.
 *
 * @param fromStart true when the song at [position] isn't the one that was playing, so the saved playback
 * position isn't its position and it starts from the beginning.
 */
data class QueuePosition(
    val position: Int,
    val fromStart: Boolean
)

/**
 * The position to save for [currentPosition] in [songs] (the queue as the shuffle mode presents it), once the
 * songs that aren't in the library are left out of the saved queue. When the current song is itself one of those,
 * it's the library song after it, or failing that the one before. Null when there's no position, or no library
 * song to save.
 */
fun savedQueuePosition(
    songs: List<Song>,
    currentPosition: Int?
): QueuePosition? = currentPosition?.let {
    remainingQueuePosition(songs.map { song -> song.isInLibrary }, currentPosition)
}

/**
 * The position to restore [savedPosition] in [savedIds] to, once the songs missing from [restoredIds] are dropped.
 * When the saved song is itself missing, it's the restored song after it, or failing that the one before. Null
 * when the position is out of range or nothing was restored.
 */
fun restoredQueuePosition(
    savedIds: List<Long>,
    savedPosition: Int,
    restoredIds: Set<Long>
): QueuePosition? = remainingQueuePosition(savedIds.map { songId -> songId in restoredIds }, savedPosition)

/** Where [position] lands once the items [kept] marks false are removed; see [savedQueuePosition]. */
private fun remainingQueuePosition(
    kept: List<Boolean>,
    position: Int
): QueuePosition? {
    if (position !in kept.indices) return null
    val keptCount = kept.count { it }
    if (keptCount == 0) return null
    val keptBefore = kept.take(position).count { it }
    return QueuePosition(position = minOf(keptBefore, keptCount - 1), fromStart = !kept[position])
}

private fun String?.toSongIds(): List<Long>? = this?.split(',')?.map { id -> id.toLong() }
