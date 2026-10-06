package com.simplecityapps.shuttle.model

/**
 * What an artist's image is (#781, #823), one rule for Android and iOS and for every place an artist is pictured: their
 * page's full-bleed hero (whose colours are seeded from it) and their rows in lists and search. Tried in this order, the
 * first that loads being the one shown:
 *
 * 1. The artist's own image: one beside their files (Android's artist.jpg) or the media server's (Jellyfin, Emby, Plex).
 * 2. The S2 artwork API's artist image, only when [onlineLookup]: the API looks artists up by name alone and says
 *    nothing about how well it matched, so it's trusted only for an artist whose tags pin them down ([of]).
 * 3. [fallbackAlbum]'s cover, as a full square.
 *
 * An artist image (1 or 2) smaller than [MIN_ARTIST_IMAGE_SIZE] counts as absent, so the hero never
 * shows a thumbnail upscaled; the album cover, the last resort, is taken at any size.
 */
data class ArtistHeroArtwork(
    val artist: AlbumArtist,
    /** Whether to ask the S2 artwork API for the artist's image: their songs carry one MusicBrainz artist id for them. */
    val onlineLookup: Boolean,
    /** The cover to fall back to: their most played album, else their newest ([topAlbum]). */
    val fallbackAlbum: Album?,
) {
    companion object {
        /**
         * The smallest an artist image's shorter side may be, in pixels, to be shown (the image loaders check it as they fetch): about a phone's full-bleed hero at
         * 2x. A server's thumbnail-sized artist image falls through to the next source instead (#823).
         */
        const val MIN_ARTIST_IMAGE_SIZE = 500

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
            onlineLookup = (artist.name ?: artist.friendlyArtistName) != null && musicBrainzArtistId(artist, songs) != null,
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
        ): String? {
            val ids = songs.mapNotNullTo(HashSet()) { song ->
                val ids = if (song.isAlbumArtist(artist.groupKey)) {
                    song.mbAlbumArtistIds
                } else {
                    song.mbArtistIds?.takeIf { song.artistCredits.singleOrNull()?.groupKey == artist.groupKey }
                }
                musicBrainzIds(ids.orEmpty()).singleOrNull()
            }
            return ids.singleOrNull()?.takeIf { it != VARIOUS_ARTISTS_MBID }
        }

        /** The most played of [albums], the newest breaking a tie; the newest when none has been played. */
        fun topAlbum(albums: List<Album>): Album? = albums.maxWithOrNull(
            compareBy<Album> { it.playCount }
                .thenBy(nullsFirst()) { it.year }
                .thenBy(nullsFirst()) { it.dateAdded },
        )
    }
}
