package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong

object HomeScenarios {
    val okComputer = createAlbum("OK Computer", "Radiohead", year = 1997)
    val kidA = createAlbum("Kid A", "Radiohead", year = 2000)
    val mezzanine = createAlbum("Mezzanine", "Massive Attack", year = 1998)
    val homogenic = createAlbum("Homogenic", "Björk", year = 1997)
    val dummy = createAlbum("Dummy", "Portishead", playCount = 14)
    val moonSafari = createAlbum("Moon Safari", "Air", playCount = 3)
    val boardsOfCanada = createAlbumArtist("Boards of Canada", albumCount = 4)
    val cocteauTwins = createAlbumArtist("Cocteau Twins", albumCount = 1)
    val songs = listOf(createSong(id = 1, name = "Airbag", albumArtist = "Radiohead", album = "OK Computer"))

    val loading = HomeUiState.Loading

    val empty = HomeUiState.Empty

    val content = HomeUiState.Content(
        showWhatsNew = false,
        recentlyPlayed = listOf(okComputer, mezzanine, homogenic, kidA),
        recentlyAdded = listOf(kidA, homogenic, okComputer),
        mostPlayed = listOf(dummy, moonSafari),
        somethingDifferent = listOf(boardsOfCanada, cocteauTwins),
        songs = songs,
    )

    val whatsNew = content.copy(showWhatsNew = true)

    /** A library that's never been played: only Recently added and Something different have anything. */
    val unplayed = content.copy(recentlyPlayed = emptyList(), mostPlayed = emptyList())
}
