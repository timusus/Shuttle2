package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * The artwork seed for a surface tinted by [song]'s artwork (the player, a detail screen's hero): the seed of its
 * artwork while the Colour from artwork setting is on, [ArtworkSeed.None] while it's off or there's no song. It starts
 * at [ArtworkSeed.Loading] and extracts again only when the song's artwork changes, keyed like the image itself (a
 * track with its own artwork seeds on its own), through the shared [ArtworkSeedSource] and its cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveArtworkSeed @Inject constructor(
    private val seedSource: ArtworkSeedSource,
    private val observeSetting: ObserveSetting,
) {
    operator fun invoke(song: Flow<Song?>): Flow<ArtworkSeed> = observeSeed(song, observeSetting, ::sameArtwork) { seedSource.seedFor(it) }

    private fun sameArtwork(old: Song, new: Song): Boolean = (old.albumArtist ?: old.friendlyArtistName) == (new.albumArtist ?: new.friendlyArtistName) &&
        old.album == new.album &&
        old.name == new.name &&
        old.artworkVersion == new.artworkVersion
}

/**
 * [model]'s artwork seed while the Colour from artwork setting is on, else [ArtworkSeed.None]: [ArtworkSeed.Loading]
 * first, then extracted again only when [sameArtwork] says the artwork changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T : Any> observeSeed(
    model: Flow<T?>,
    observeSetting: ObserveSetting,
    sameArtwork: (T, T) -> Boolean,
    seedFor: suspend (T) -> ArtworkSeed,
): Flow<ArtworkSeed> = combine(model, observeSetting(AppearanceSettings.ColourFromArtwork)) { model, enabled -> model?.takeIf { enabled } }
    .distinctUntilChanged { old, new -> if (old == null || new == null) old == new else sameArtwork(old, new) }
    .mapLatest { model -> model?.let { seedFor(it) } ?: ArtworkSeed.None }
    .onStart { emit(ArtworkSeed.Loading) }
