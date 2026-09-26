package com.simplecityapps.shuttle.model

import kotlin.time.Instant

data class Album(
    val name: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val playCount: Int,
    val lastSongPlayed: Instant?,
    val lastSongCompleted: Instant?,
    val groupKey: AlbumGroupKey?,
    val mediaProviders: List<MediaProviderType>,
    // Changes whenever any of the album's songs' artworkVersion does.
    val artworkVersion: String? = null
) {
    val friendlyArtistName: String?
        by lazy {
            if (artists.isNotEmpty()) {
                if (artists.size == 1) {
                    artists.first()
                } else {
                    artists.groupBy { it.lowercase().removeArticles() }
                        .map { map -> map.value.maxByOrNull { it.length } }
                        .joinToString(", ")
                        .ifEmpty { null }
                }
            } else {
                null
            }
        }
}
