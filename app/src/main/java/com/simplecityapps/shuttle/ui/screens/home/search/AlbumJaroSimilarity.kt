package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Album

data class AlbumJaroSimilarity(
    val album: Album,
    val query: String
) {
    val nameJaroSimilarity = StringComparison.jaroWinklerDistance(query, album.name)
    val artistNameJaroSimilarity = StringComparison.jaroWinklerDistance(query, album.albumArtist)
}