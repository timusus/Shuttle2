package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong
import com.simplecityapps.shuttle.ui.text.StringKey
import kotlin.time.Instant

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
    ) = HomeSection(id, title, subtitle(id), items.toList())

    /** The subtitle the shared assembly gives a section with history (#671). */
    private fun subtitle(id: HomeSectionId): StringKey? = when (id) {
        HomeSectionId.JumpBackIn -> null
        HomeSectionId.AroundThisTime -> StringKey.HOME_AROUND_THIS_TIME_SUBTITLE
        HomeSectionId.HeavyRotation -> StringKey.HOME_HEAVY_ROTATION_SUBTITLE
        HomeSectionId.Rediscover -> StringKey.HOME_REDISCOVER_SUBTITLE
        HomeSectionId.RecentlyAdded -> StringKey.HOME_RECENTLY_ADDED_SUBTITLE
        HomeSectionId.GenrePicks -> StringKey.HOME_GENRE_PICKS_SUBTITLE
        HomeSectionId.ShuffleAll -> null
    }

    private val genrePicks = section(HomeSectionId.GenrePicks, HomeSectionTitle.GenrePicks, *genres.map { HomeItem.GenreItem(it) }.toTypedArray())

    val smartPlaylist = HomeItem.SmartPlaylistItem(SmartPlaylistId.Favourites)

    /** Eight things played lately, of every kind: the resume card and six tiles, and one more than the grid shows. */
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
            section(HomeSectionId.HeavyRotation, HomeSectionTitle.HeavyRotation, HomeItem.AlbumItem(softFocus), HomeItem.AlbumItem(signalRoom)),
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

    /** Two shelves: every row fits on a phone screen with room to spare (#942). */
    val fewRows = content.copy(sections = content.sections.filter { it.id == HomeSectionId.HeavyRotation || it.id == HomeSectionId.RecentlyAdded })

    private fun progress(
        songName: String?,
        fraction: Float,
        shuffled: Boolean = false,
        finished: Boolean = false,
        hoursAgo: Long = 2,
    ) = HomeItemProgress(
        songName = songName,
        positionMs = 30_000,
        fraction = fraction,
        shuffled = shuffled,
        finished = finished,
        updatedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis() - hoursAgo * 3_600_000),
    )

    /**
     * Phase Garden's queue left on its fifth track of twelve, "Glasshouse", two hours ago (#670, #706), under way in
     * Road Trip ("Open Roads"), shuffled in Saltmarsh Choir and played through in Harbour Weather.
     */
    val resuming = content.copy(
        sections = listOf(
            jumpBackIn.copy(
                progress = mapOf(
                    HomeItem.AlbumItem(phaseGarden).key to progress("Glasshouse", fraction = 4.5f / 12),
                    HomeItem.PlaylistItem(roadTrip).key to progress("Open Roads", fraction = 0.5f),
                    HomeItem.ArtistItem(saltmarshChoir).key to progress("Tidal Pull", fraction = 0.3f, shuffled = true),
                    HomeItem.AlbumItem(harbourWeather).key to progress("Last Light", fraction = 1f, finished = true),
                ),
            ),
        ) + content.sections.drop(1),
    )

    /** Phase Garden played to the end: nothing left to resume, so its card says Play again. */
    val finished = content.copy(
        sections = listOf(
            jumpBackIn.copy(progress = mapOf(HomeItem.AlbumItem(phaseGarden).key to progress("Glasshouse", fraction = 1f, finished = true))),
        ) + content.sections.drop(1),
    )

    /** Phase Garden's queue shuffled: the card shows a shuffle glyph and no bar. */
    val shuffled = content.copy(
        sections = listOf(
            jumpBackIn.copy(progress = mapOf(HomeItem.AlbumItem(phaseGarden).key to progress("Glasshouse", fraction = 0.3f, shuffled = true))),
        ) + content.sections.drop(1),
    )

    /** A library that's never been played (cold start): Recently added, Genre picks and Shuffle all. */
    val unplayed = content.copy(
        sections = listOf(
            section(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, HomeItem.AlbumItem(nightBus), HomeItem.AlbumItem(blueHours), HomeItem.AlbumItem(phaseGarden)),
            genrePicks.copy(subtitle = StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE),
            section(HomeSectionId.ShuffleAll, HomeSectionTitle.ShuffleAll),
        ),
    )
}
