package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Song

data class SongJaroSimilarity(
    val song: Song,
    val query: String
) {
    val nameJaroSimilarity = song.name?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumNameJaroSimilarity = song.album?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumArtistNameJaroSimilarity = song.albumArtist?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
}