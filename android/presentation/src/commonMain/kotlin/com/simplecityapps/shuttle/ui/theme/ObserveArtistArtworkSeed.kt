package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.settings.ObserveSetting
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** Seeds an artist page from its hero image's first loadable candidate; otherwise as [ObserveArtworkSeed]. */
class ObserveArtistArtworkSeed @Inject constructor(
    private val seedSource: ArtworkSeedSource,
    private val observeSetting: ObserveSetting,
) {
    operator fun invoke(hero: Flow<ArtistHeroArtwork?>): Flow<ArtworkSeed> = observeSeed(hero, observeSetting, ::sameArtwork) { seedSource.seedFor(it) }

    private fun sameArtwork(old: ArtistHeroArtwork, new: ArtistHeroArtwork): Boolean = sameArtwork(old.artist, new.artist) &&
        old.onlineLookup == new.onlineLookup &&
        sameArtwork(old.fallbackAlbum, new.fallbackAlbum)

    private fun sameArtwork(old: AlbumArtist, new: AlbumArtist): Boolean = (old.name ?: old.friendlyArtistName) == (new.name ?: new.friendlyArtistName) &&
        old.artworkVersion == new.artworkVersion

    private fun sameArtwork(old: Album?, new: Album?): Boolean = old?.groupKey == new?.groupKey && old?.artworkVersion == new?.artworkVersion
}
