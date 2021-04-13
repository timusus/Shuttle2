package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Album

data class AlbumJaroSimilarity(
    val album: Album,
    val query: String
) {
    val nameJaroSimilarity = album.name?.let { name -> StringComparison.jaroWinklerDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = album.albumArtist?.let { albumArtist -> StringComparison.jaroWinklerDistance(query, albumArtist) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
}