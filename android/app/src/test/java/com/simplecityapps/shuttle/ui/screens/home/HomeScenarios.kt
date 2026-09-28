package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toSong

/** Home over the sample library, so its tiles load the generated covers under `SampleArtworkCoil`. */
object HomeScenarios {
    val phaseGarden = SampleLibrary.album("phase-garden").toAlbum()
    val nightBus = SampleLibrary.album("night-bus-frequencies").toAlbum()
    val harbourWeather = SampleLibrary.album("harbour-weather").toAlbum()
    val blueHours = SampleLibrary.album("blue-hours").toAlbum()
    val softFocus = SampleLibrary.album("soft-focus").toAlbum()
    val signalRoom = SampleLibrary.album("signal-room").toAlbum()
    val saltmarshChoir = SampleLibrary.artist("Saltmarsh Choir").toAlbumArtist()
    val genres = SampleLibrary.genres.map { it.toGenre() }.sortedByDescending { it.songCount }.take(4)
    private val phaseGardenSongs = SampleLibrary.album("phase-garden").songs.map { it.toSong() }

    /** Paused partway into Phase Garden, 2:14 from the end of its first song. */
    val resume = ResumeQueue(song = phaseGardenSongs.first(), songs = phaseGardenSongs, timeLeftMs = 134_000, playing = false)

    val loading = HomeUiState.Loading

    val empty = HomeUiState.Empty

    private fun section(
        id: HomeSectionId,
        title: HomeSectionTitle,
        vararg items: HomeItem,
    ) = HomeSection(id, title, items.toList())

    private val genrePicks = section(HomeSectionId.GenrePicks, HomeSectionTitle.GenrePicks, *genres.map { HomeItem.GenreItem(it) }.toTypedArray())

    val content = HomeUiState.Content(
        showWhatsNew = false,
        sections = listOf(
            section(
                HomeSectionId.JumpBackIn,
                HomeSectionTitle.JumpBackIn,
                HomeItem.AlbumItem(phaseGarden),
                HomeItem.ArtistItem(saltmarshChoir),
                HomeItem.AlbumItem(harbourWeather),
            ),
            section(HomeSectionId.OnRepeat, HomeSectionTitle.OnRepeat, HomeItem.AlbumItem(softFocus), HomeItem.AlbumItem(signalRoom)),
            section(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, HomeItem.AlbumItem(nightBus), HomeItem.AlbumItem(blueHours)),
            genrePicks,
        ),
        resume = resume,
    )

    val playing = content.copy(resume = resume.copy(playing = true))

    val whatsNew = content.copy(showWhatsNew = true)

    /** A library that's never been played (cold start): no queue to resume; Recently added, Genre picks and Shuffle all. */
    val unplayed = content.copy(
        resume = null,
        sections = listOf(
            section(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, HomeItem.AlbumItem(nightBus), HomeItem.AlbumItem(blueHours), HomeItem.AlbumItem(phaseGarden)),
            genrePicks,
            section(HomeSectionId.ShuffleAll, HomeSectionTitle.ShuffleAll),
        ),
    )
}
