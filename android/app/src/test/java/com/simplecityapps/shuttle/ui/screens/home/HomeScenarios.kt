package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong

/** Home over the sample library, so its tiles load the generated covers under `SampleArtworkCoil`. */
object HomeScenarios {
    val phaseGarden = SampleLibrary.album("phase-garden").toAlbum()
    val nightBus = SampleLibrary.album("night-bus-frequencies").toAlbum()
    val harbourWeather = SampleLibrary.album("harbour-weather").toAlbum()
    val lighthouseFerry = SampleLibrary.album("lighthouse-ferry").toAlbum()
    val cassetteSummer = SampleLibrary.album("cassette-summer").toAlbum()
    val blueHours = SampleLibrary.album("blue-hours").toAlbum()
    val softFocus = SampleLibrary.album("soft-focus").toAlbum()
    val signalRoom = SampleLibrary.album("signal-room").toAlbum()
    val estuary = SampleLibrary.album("estuary").toAlbum()
    val saltmarshChoir = SampleLibrary.artist("Saltmarsh Choir").toAlbumArtist()
    val marlowVane = SampleLibrary.artist("Marlow Vane").toAlbumArtist()
    val paleMeridian = SampleLibrary.artist("Pale Meridian").toAlbumArtist()
    val roadTrip = SampleLibrary.playlist("Road Trip").toPlaylist(id = 1)
    val lateNight = SampleLibrary.playlist("Late Night").toPlaylist(id = 2)
    val genres = SampleLibrary.genres.map { it.toGenre() }.sortedByDescending { it.songCount }.take(4)

    /** A song from each of four albums, for a playlist's or genre's mosaic. */
    private fun covers(vararg albums: String): List<Song> = albums.map { SampleLibrary.album(it).songs.first().toSong() }

    val loading = HomeUiState.Loading

    val empty = HomeUiState.Empty

    private fun section(
        id: HomeSectionId,
        title: HomeSectionTitle,
        vararg items: HomeItem,
    ) = HomeSection(id, title, items.toList())

    private val genrePicks = section(HomeSectionId.GenrePicks, HomeSectionTitle.GenrePicks, *genres.map { HomeItem.GenreItem(it) }.toTypedArray())

    val smartPlaylist = HomeItem.SmartPlaylistItem(SmartPlaylistId.Favourites)

    /** Eight things played lately, of every kind: two full rows of the grid at any width. */
    val jumpBackIn = section(
        HomeSectionId.JumpBackIn,
        HomeSectionTitle.JumpBackIn,
        HomeItem.AlbumItem(phaseGarden),
        HomeItem.ArtistItem(saltmarshChoir),
        HomeItem.AlbumItem(harbourWeather),
        HomeItem.PlaylistItem(roadTrip),
        HomeItem.ArtistItem(paleMeridian),
        HomeItem.AlbumItem(lighthouseFerry),
        HomeItem.AlbumItem(cassetteSummer),
        HomeItem.ArtistItem(marlowVane),
    )

    val content = HomeUiState.Content(
        showWhatsNew = false,
        sections = listOf(
            jumpBackIn,
            section(HomeSectionId.OnRepeat, HomeSectionTitle.OnRepeat, HomeItem.AlbumItem(softFocus), HomeItem.AlbumItem(signalRoom)),
            section(HomeSectionId.Rediscover, HomeSectionTitle.Rediscover, HomeItem.AlbumItem(estuary), smartPlaylist, HomeItem.PlaylistItem(lateNight)),
            section(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, HomeItem.AlbumItem(nightBus), HomeItem.AlbumItem(blueHours)),
            genrePicks,
        ),
        covers = mapOf(
            HomeItem.PlaylistItem(roadTrip).key to covers("night-bus-frequencies", "blue-hours", "smoke-rings", "undertow"),
            HomeItem.PlaylistItem(lateNight).key to covers("soft-focus", "slow-bloom", "lantern-hours", "weather-systems"),
            HomeItem.GenreItem(genres.first()).key to covers("estuary", "loose-change", "low-tide-sessions", "signal-room"),
        ),
    )

    val whatsNew = content.copy(showWhatsNew = true)

    /** A library that's never been played (cold start): Recently added, Genre picks and Shuffle all. */
    val unplayed = content.copy(
        sections = listOf(
            section(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, HomeItem.AlbumItem(nightBus), HomeItem.AlbumItem(blueHours), HomeItem.AlbumItem(phaseGarden)),
            genrePicks,
            section(HomeSectionId.ShuffleAll, HomeSectionTitle.ShuffleAll),
        ),
    )
}
