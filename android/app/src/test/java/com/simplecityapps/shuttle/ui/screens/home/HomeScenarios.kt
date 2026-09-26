package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toSong

/** Home over the sample library, so its tiles load the generated covers under `SampleArtworkCoil`. */
object HomeScenarios {
    val phaseGarden = SampleLibrary.album("phase-garden").toAlbum()
    val nightBus = SampleLibrary.album("night-bus-frequencies").toAlbum()
    val harbourWeather = SampleLibrary.album("harbour-weather").toAlbum()
    val blueHours = SampleLibrary.album("blue-hours").toAlbum()
    val softFocus = SampleLibrary.album("soft-focus").toAlbum().copy(playCount = 14)
    val signalRoom = SampleLibrary.album("signal-room").toAlbum().copy(playCount = 3)
    val saltmarshChoir = SampleLibrary.artist("Saltmarsh Choir").toAlbumArtist()
    val paleMeridian = SampleLibrary.artist("Pale Meridian").toAlbumArtist()
    val songs = listOf(SampleLibrary.album("phase-garden").songs.first().toSong())

    val loading = HomeUiState.Loading

    val empty = HomeUiState.Empty

    val content = HomeUiState.Content(
        showWhatsNew = false,
        recentlyPlayed = listOf(phaseGarden, harbourWeather, blueHours, nightBus),
        recentlyAdded = listOf(nightBus, blueHours, phaseGarden),
        mostPlayed = listOf(softFocus, signalRoom),
        somethingDifferent = listOf(saltmarshChoir, paleMeridian),
        songs = songs,
    )

    val whatsNew = content.copy(showWhatsNew = true)

    /** A library that's never been played: only Recently added and Something different have anything. */
    val unplayed = content.copy(recentlyPlayed = emptyList(), mostPlayed = emptyList())
}
