package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.LoadLyrics
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * The lyrics of the current song. Songs don't carry their lyrics, so this is null until loaded and loading never
 * holds the rest of the player back. The online setting re-runs the lookup but only a new song blanks the lyrics, so
 * toggling it doesn't flash them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLyrics @Inject constructor(
    private val loadLyrics: LoadLyrics,
) {
    /** [onMissing] runs when a lookup for a song finds no lyrics. */
    operator fun invoke(
        songs: Flow<Song?>,
        onMissing: () -> Unit,
    ): Flow<String?> = flow {
        var shownId: Any? = Any()
        emitAll(
            combine(
                songs.distinctUntilChanged { old, new -> old?.id == new?.id },
                loadLyrics.onlineEnabled.distinctUntilChanged(),
            ) { song, _ -> song }.flatMapLatest { song ->
                val newSong = song?.id != shownId
                shownId = song?.id
                lyricsFor(song, resetFirst = newSong, onMissing)
            },
        )
    }.distinctUntilChanged()

    private fun lyricsFor(
        song: Song?,
        resetFirst: Boolean,
        onMissing: () -> Unit,
    ): Flow<String?> = if (song == null) {
        flowOf(null)
    } else {
        flow {
            val text = try {
                loadLyrics(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (text == null) onMissing()
            emit(text)
        }.onStart { if (resetFirst) emit(null) }
    }
}
