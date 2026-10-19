package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.shuttle.model.Song

/** What the excluded songs screen asks of its host. */
sealed interface ExcludedSongsEvent {
    data object NavigateUp : ExcludedSongsEvent

    data class Include(val song: Song) : ExcludedSongsEvent

    data object IncludeAll : ExcludedSongsEvent
}

class ExcludedSongsCallbacks(
    val onNavigateUp: () -> Unit,
    val onInclude: (Song) -> Unit,
    val onIncludeAll: () -> Unit
) {
    fun onEvent(event: ExcludedSongsEvent) = when (event) {
        ExcludedSongsEvent.NavigateUp -> onNavigateUp()
        is ExcludedSongsEvent.Include -> onInclude(event.song)
        ExcludedSongsEvent.IncludeAll -> onIncludeAll()
    }
}

sealed interface SongMenuEvent {
    data class Open(val song: Song) : SongMenuEvent

    data object Dismiss : SongMenuEvent

    data class Include(val song: Song) : SongMenuEvent
}

/** [openSongId] is the song whose menu shows, if any; [event] is what the step asks of the host. */
data class SongMenuStep(val openSongId: Long?, val event: ExcludedSongsEvent? = null)

/** Including a song closes its menu. */
fun onSongMenuEvent(event: SongMenuEvent): SongMenuStep = when (event) {
    is SongMenuEvent.Open -> SongMenuStep(openSongId = event.song.id)
    SongMenuEvent.Dismiss -> SongMenuStep(openSongId = null)
    is SongMenuEvent.Include -> SongMenuStep(openSongId = null, event = ExcludedSongsEvent.Include(event.song))
}
