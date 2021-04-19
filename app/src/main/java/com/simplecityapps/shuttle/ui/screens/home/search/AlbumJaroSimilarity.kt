package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.Album

data class AlbumJaroSimilarity(
    val album: Album,
    val query: String
) {
    val nameJaroSimilarity = album.name?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(),  emptyMap())
    val albumArtistNameJaroSimilarity = album.albumArtist?.let { albumArtist -> StringComparison.jaroWinklerMultiDistance(query, albumArtist) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = album.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
}