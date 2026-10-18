package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.shuttle.model.Song

/** Nothing in the queue yet, with the saved queue still to be restored: the saved song stands in for it meanwhile. */
internal val QueueState.isAwaitingRestore: Boolean get() = items.isEmpty() && !isRestored

/** Until the queue is restored, [savedSong] (the song it was left on) stands in for it, when there is one. */
internal fun QueueState.toPlayerUiState(savedSong: PlayerSong? = null): PlayerUiState {
    if (isAwaitingRestore) {
        return savedSong?.let { PlayerUiState(hasQueue = true, current = it, items = listOf(it)) } ?: PlayerUiState.Unknown
    }
    val currentIndex = currentPosition ?: -1
    val rows = items.mapIndexed { index, item ->
        item.song.toPlayerSong(
            uid = item.uid,
            position = when {
                item.isCurrent -> QueuePosition.Current
                index < currentIndex -> QueuePosition.Played
                else -> QueuePosition.Upcoming
            },
        )
    }
    return PlayerUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull { it.position == QueuePosition.Current }, items = rows)
}

private fun Song.toPlayerSong(
    uid: Long,
    position: QueuePosition,
) = PlayerSong(
    uid = uid,
    title = name.orEmpty(),
    artist = friendlyArtistName ?: albumArtist,
    album = album,
    durationMs = duration,
    position = position,
    song = this,
)

/** The saved song, as the current and only row of a queue not yet restored; no queue row has its uid. */
internal fun NowPlayingSnapshot.toPlayerSong(): PlayerSong = toSong().toPlayerSong(uid = -1, position = QueuePosition.Current)

internal fun RepeatMode.toS2RepeatMode(): S2RepeatMode = when (this) {
    RepeatMode.Off -> S2RepeatMode.Off
    RepeatMode.All -> S2RepeatMode.All
    RepeatMode.One -> S2RepeatMode.One
}
