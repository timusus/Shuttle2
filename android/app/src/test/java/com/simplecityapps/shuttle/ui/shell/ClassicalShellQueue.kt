package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.model.nowPlayingWork
import com.simplecityapps.shuttle.ui.preview.sampleSongs
import com.simplecityapps.shuttle.ui.sampleSeed
import com.simplecityapps.shuttle.ui.shell.player.PlayerSong
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.QueuePosition

private val SymphonyMovements = listOf("Allegro con brio", "Andante con moto", "Scherzo", "Allegro")

/** One invented work by the composer Harrow, [playing] being the current movement, with `work` filled in as the view model does. */
fun classicalShellQueue(
    movements: Int = SymphonyMovements.size,
    playing: Int = 1,
): PlayerUiState {
    val songs = sampleSongs(movements).mapIndexed { index, song ->
        song.copy(
            name = SymphonyMovements.getOrNull(index)?.takeIf { movements == SymphonyMovements.size } ?: "Variation ${index + 1}",
            composer = "Harrow",
            composerSort = "Harrow, Ines",
            conductor = "Mira Calder",
            work = "Symphony No. 5 in C minor, Op. 67",
            track = index + 1,
            duration = (4 + index % 3) * 60_000 + 15_000,
        )
    }
    val rows = songs.mapIndexed { index, song ->
        PlayerSong(
            uid = index.toLong(),
            title = song.name.orEmpty(),
            artist = song.artists.first(),
            album = song.album.orEmpty(),
            durationMs = song.duration,
            position = when {
                index < playing -> QueuePosition.Played
                index == playing -> QueuePosition.Current
                else -> QueuePosition.Upcoming
            },
            song = song,
        )
    }
    val current = rows[playing]
    return PlayerUiState(
        hasQueue = true,
        current = current,
        items = rows,
        seed = sampleSeed(current.album.orEmpty()),
        work = nowPlayingWork(songs, playing),
    )
}
