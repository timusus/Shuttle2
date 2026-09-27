package com.simplecityapps.shuttle.ui.theme

/**
 * Where an artwork seed is in its extraction: the colour a surface tinted by artwork (the player, a detail screen's
 * hero) builds its scheme from. Platform-neutral, so shared UI state can carry it; each platform turns [Available.argb]
 * into its own colour where it draws (Android: `ArtworkTheme` in `:android:designsystem`).
 */
sealed interface ArtworkSeed {
    /** Extraction is in flight: keep showing the previous seed. */
    data object Loading : ArtworkSeed

    /** No artwork, or extraction failed: fall back to the root scheme. */
    data object None : ArtworkSeed

    /** The seed colour, as sRGB `0xAARRGGBB`. */
    data class Available(val argb: Int) : ArtworkSeed
}
