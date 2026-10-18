package com.simplecityapps.shuttle.model

/** An artist with no albums of their own is credited only: the Artists list leaves them out, but search still finds them. */
data class AlbumArtist(
    val name: String?,
    val artists: List<String>,
    val albumCount: Int,
    /** Their songs: those of their own albums, and those crediting them on others. */
    val songCount: Int,
    /** How many times their songs have been played through, all together. */
    val playCount: Int,
    val groupKey: AlbumArtistGroupKey,
    val mediaProviders: List<MediaProviderType>,
    // Changes whenever any of the artist's songs' artworkVersion does.
    val artworkVersion: String? = null,
    /** Albums of other album artists with songs crediting them: their Appears On. */
    val appearsOnCount: Int = 0
) {
    /** Whether they're the album artist of any album; only these are listed as Artists. */
    val isAlbumArtist: Boolean get() = albumCount > 0

    val friendlyArtistName: String? by lazy {
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
