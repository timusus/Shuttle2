package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Song

data class SongJaroSimilarity(
    val song: Song,
    val query: String
) {
    val nameJaroSimilarity = StringComparison.jaroWinklerDistance(query, song.name)
    val albumNameJaroSimilarity = StringComparison.jaroWinklerDistance(query, song.album)
    val artistNameJaroSimilarity = StringComparison.jaroWinklerDistance(query, song.albumArtist)
}