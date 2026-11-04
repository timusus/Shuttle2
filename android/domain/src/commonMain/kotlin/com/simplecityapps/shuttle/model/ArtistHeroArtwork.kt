package com.simplecityapps.shuttle.model

/** One image rule for every artist surface; the online lookup is by name unless tags contradict it or the artist is Various Artists. */
data class ArtistHeroArtwork(
    val artist: AlbumArtist,
    /** Whether to ask the S2 artwork API for the artist's image: they have a name and their songs don't disagree on a MusicBrainz id. */
    val onlineLookup: Boolean,
    /** The cover to fall back to: their most played album, else their newest ([topAlbum]). */
    val fallbackAlbum: Album?,
) {
    companion object {
        /** Shorter-side minimum for an artist image (about a phone's full-bleed hero at 2x); smaller ones fall through. */
        const val MIN_ARTIST_IMAGE_SIZE = 500

        /**
         * The minimum sizes to try an artist's image sources at, in order: a large artist image beats a small one, and
         * any artist image beats an album cover. Both platforms' chains repeat their artist sources once per size.
         */
        val ARTIST_IMAGE_MINIMUM_SIZES = listOf(MIN_ARTIST_IMAGE_SIZE, 0)

        /** MusicBrainz's "Various Artists": an id, but no one artist's image. */
        private const val VARIOUS_ARTISTS_MBID = "89ad4ac3-39f7-470e-963a-56509c546377"

        /**
         * [artist]'s hero from their [songs] (their own albums' and those crediting them), their own [albums] and the
         * albums they [appearsOn], whose top album stands in for a credited-only artist's.
         */
        fun of(
            artist: AlbumArtist,
            albums: List<Album>,
            songs: List<Song>,
            appearsOn: List<Album> = emptyList(),
        ): ArtistHeroArtwork = ArtistHeroArtwork(
            artist = artist,
            onlineLookup = (artist.name ?: artist.friendlyArtistName) != null && !isVariousArtists(artist) && carriedIds(artist, songs).let { ids ->
                ids.size <= 1 && VARIOUS_ARTISTS_MBID !in ids
            },
            fallbackAlbum = topAlbum(albums) ?: topAlbum(appearsOn),
        )

        /**
         * The one MusicBrainz artist id [songs] carry for [artist], or null when they carry none or disagree. Their own
         * albums' songs give it as the album artist id; a song only crediting them, as its artist id when it credits
         * no one else. Songs without an id don't count against it.
         */
        fun musicBrainzArtistId(
            artist: AlbumArtist,
            songs: List<Song>,
        ): String? = carriedIds(artist, songs).singleOrNull()?.takeIf { it != VARIOUS_ARTISTS_MBID }

        private fun carriedIds(
            artist: AlbumArtist,
            songs: List<Song>,
        ): Set<String> = songs.mapNotNullTo(HashSet()) { song ->
            val ids = if (song.isAlbumArtist(artist.groupKey)) {
                song.mbAlbumArtistIds
            } else {
                song.mbArtistIds?.takeIf { song.artistCredits.singleOrNull()?.groupKey == artist.groupKey }
            }
            musicBrainzIds(ids.orEmpty()).singleOrNull()
        }

        private fun isVariousArtists(artist: AlbumArtist): Boolean = artist.groupKey == AlbumArtistGroupKey(AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS))

        /** The most played of [albums], the newest breaking a tie; the newest when none has been played. */
        fun topAlbum(albums: List<Album>): Album? = albums.maxWithOrNull(
            compareBy<Album> { it.playCount }
                .thenBy(nullsFirst()) { it.year }
                .thenBy(nullsFirst()) { it.dateAdded },
        )
    }
}
