package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.nowPlayingWork
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What Now Playing shows beside the queue itself: the artwork seed and image, the classical work and the delivered format. */
class ObserveNowPlayingDecor @Inject constructor(
    private val observeArtworkSeed: ObserveArtworkSeed,
    private val observeSetting: ObserveSetting,
    private val deliveredFormats: DeliveredFormats,
) {
    fun seed(currentSong: Flow<Song?>): Flow<ArtworkSeed> = observeArtworkSeed(currentSong, observeSetting(ArtworkSettings.NowPlayingArtworkSource))

    /** [state] with its image, work and delivered format filled in. */
    fun decorate(state: Flow<PlayerUiState>): Flow<PlayerUiState> = state
        .combine(observeSetting(ArtworkSettings.NowPlayingArtworkSource)) { state, image ->
            state.copy(nowPlayingImage = image)
        }.combine(observeSetting(AppearanceSettings.ClassicalDisplay)) { state, enabled ->
            val index = state.items.indexOfFirst { it.position == QueuePosition.Current }
            // A view, not a copy: the work is found from the songs around the current one only.
            val items = state.items
            val songs = object : AbstractList<Song>() {
                override val size get() = items.size

                override fun get(index: Int) = items[index].song
            }
            state.copy(work = if (enabled && index >= 0) nowPlayingWork(songs, index) else null)
        }.combine(deliveredFormats.byPath) { state, delivered ->
            state.copy(delivered = state.current?.song?.path?.let(delivered::get))
        }
}
