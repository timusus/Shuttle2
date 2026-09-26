package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.ui.shell.player.ArtworkSeedSource
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * The artwork seed for a detail screen's hero: the seed of [song]'s album artwork while the Colour from artwork
 * setting is on, [ArtworkSeed.None] while it's off or there's no song. It starts at [ArtworkSeed.Loading] and
 * extracts again only when the song's album changes, through the player's [ArtworkSeedSource] and its cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveArtworkSeed @Inject constructor(
    private val seedSource: ArtworkSeedSource,
    private val observeSetting: ObserveSetting,
) {
    operator fun invoke(song: Flow<Song?>): Flow<ArtworkSeed> = combine(song, observeSetting(AppearanceSettings.ColourFromArtwork)) { song, enabled -> song?.takeIf { enabled } }
        .distinctUntilChanged(::sameArtwork)
        .mapLatest { song -> song?.let { seedSource.seedFor(it) } ?: ArtworkSeed.None }
        .onStart { emit(ArtworkSeed.Loading) }

    private fun sameArtwork(old: Song?, new: Song?): Boolean = if (old == null || new == null) old == new else old.albumGroupKey == new.albumGroupKey
}
