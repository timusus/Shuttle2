package com.simplecityapps.shuttle.model

/**
 * The tags [AlbumIdentityRule] and [ArtistCredits] read from a song: a `Song`'s (`Song.identityTags`), or a database
 * row's. The artist tags and ids are [ArtistCredits]' alone.
 */
data class AlbumIdentityTags(
    val songId: Long,
    val album: String?,
    val albumArtist: String?,
    val albumArtists: List<String>?,
    val artists: List<String>,
    val compilation: Boolean?,
    val mbAlbumId: String?,
    val serverAlbumId: String?,
    val mediaProvider: MediaProviderType,
    val path: String,
    val artistsTag: List<String>? = null,
    val mbArtistIds: List<String>? = null,
    val mbAlbumArtistIds: List<String>? = null,
    val serverArtistIds: List<String>? = null,
    val serverAlbumArtistIds: List<String>? = null
)

/** The album a song belongs to ([groupKey]) and the album artist that album shows ([albumArtistName]). */
data class AlbumIdentity(
    val groupKey: AlbumGroupKey,
    val albumArtistName: String?,
    /**
     * The artists whose album it is, each listing it among their own albums: usually its one album artist, but each of
     * an ALBUMARTISTS list's artists, and only A of an album artist "A feat. B". [groupKey] keeps the album artist as
     * tagged, so these never move an album's key.
     */
    val albumArtists: List<ArtistCredit> = listOf(ArtistCredit(albumArtistName.orEmpty(), groupKey.albumArtistGroupKey ?: AlbumArtistGroupKey(null))),
    /** The artists the album artist tag features ("A feat. B"'s B): credited on its every song, so it's on their Appears On. */
    val featuredArtists: List<String> = emptyList()
) {
    val albumArtistGroupKey: AlbumArtistGroupKey get() = groupKey.albumArtistGroupKey ?: AlbumArtistGroupKey(null)

    val albumArtistKeys: List<AlbumArtistGroupKey> get() = albumArtists.map { it.groupKey }
}

/** Groups songs per album rather than per song, so a whole library is read at once and a split album stays one. */
object AlbumIdentityRule {
    const val VARIOUS_ARTISTS = "Various Artists"

    private val VARIOUS_ARTISTS_KEY = artistKey(VARIOUS_ARTISTS)

    private val FEATURING = Regex("\\s+(?:feat\\.?|ft\\.|featuring)\\s+", RegexOption.IGNORE_CASE)

    fun resolve(songs: Collection<AlbumIdentityTags>): Map<Long, AlbumIdentity> {
        // 1. The name rule
        val nameAlbums = HashMap<Long, NameAlbum>(songs.size)
        val untagged = mutableListOf<AlbumIdentityTags>()
        songs.forEach { song ->
            val explicit = explicitAlbumArtist(song)
            nameAlbums[song.songId] = when {
                explicit != null -> NameAlbum(albumKey(song.album), artistKey(explicit), null, explicit, taggedAlbumArtists(song, explicit))
                song.compilation == true -> NameAlbum(albumKey(song.album), VARIOUS_ARTISTS_KEY, null, VARIOUS_ARTISTS)
                song.album.isNullOrBlank() -> NameAlbum(albumKey(song.album), trackArtistsKey(song.artists), null, song.artists.joinToString(", ").ifEmpty { null })
                else -> null.also { untagged += song }
            } ?: return@forEach
        }
        untagged.groupBy { song -> albumKey(song.album) to folderOf(song.path) }.forEach { (nameAndFolder, group) ->
            val (name, folder) = nameAndFolder
            val primaryArtists = group.map { song -> primaryArtist(song.artists) }
            val keys = primaryArtists.map { artist -> artist?.let(::artistKey) }.distinct()
            val nameAlbum =
                if (keys.size == 1) {
                    NameAlbum(name, keys.single(), null, primaryArtists.firstNotNullOfOrNull { it })
                } else {
                    NameAlbum(name, VARIOUS_ARTISTS_KEY, "$DIR$folder", VARIOUS_ARTISTS)
                }
            group.forEach { song -> nameAlbums[song.songId] = nameAlbum }
        }

        // 2. Ids, per name album
        val ids = HashMap<Long, String>()
        songs.groupBy { song -> nameAlbums.getValue(song.songId).key }.values.forEach { album ->
            val mbIds = album.map { song -> song.mbAlbumId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "$MB$it" } }
            val serverIds = album.map { song -> song.serverAlbumId?.trim()?.takeIf { it.isNotEmpty() }?.let { "${song.mediaProvider.name.lowercase()}:$it" } }
            val albumIds = mbIds.takeIf { it.all { id -> id != null } } ?: serverIds.takeIf { it.all { id -> id != null } } ?: return@forEach
            album.forEachIndexed { index, song -> ids[song.songId] = albumIds[index]!! }
        }

        // 3. Each album's songs share one key and album artist
        val identities = HashMap<Long, AlbumIdentity>(songs.size)
        songs.groupBy { song -> ids[song.songId]?.let { id -> AlbumBucket.ById(id) } ?: AlbumBucket.ByName(nameAlbums.getValue(song.songId).key) }
            .forEach { (bucket, album) ->
                val names = album.map { song -> nameAlbums.getValue(song.songId) }
                val largest = names.groupingBy { it.key }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<NameKey, Int>> { it.value }.thenBy { it.key.toString() })
                    .first().key
                val largestNames = names.filter { it.key == largest }
                val albumArtistName = largestNames.mapNotNull { it.albumArtistName }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val groupKey = AlbumGroupKey(largest.album, AlbumArtistGroupKey(largest.albumArtist), (bucket as? AlbumBucket.ById)?.id ?: largest.identity)
                // Whose album it is, as most of its songs tag it
                val tagged = largestNames.mapNotNull { it.albumArtists }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val identity = AlbumIdentity(
                    groupKey = groupKey,
                    albumArtistName = albumArtistName,
                    albumArtists = tagged?.albumArtists ?: listOf(ArtistCredit(albumArtistName.orEmpty(), AlbumArtistGroupKey(largest.albumArtist))),
                    featuredArtists = tagged?.featured.orEmpty()
                )
                album.forEach { song -> identities[song.songId] = identity }
            }
        return identities
    }

    /** The album name as keys hold it: case-folded, without articles or punctuation. */
    fun albumKey(album: String?): String? = album?.lowercase()?.removeArticles()

    /** An artist name as keys hold it: see [albumKey]. */
    fun artistKey(artist: String): String = artist.lowercase().removeArticles()

    private fun trackArtistsKey(artists: List<String>): String? = artists.joinToString(", ") { artistKey(it) }.ifEmpty { null }

    private fun explicitAlbumArtist(song: AlbumIdentityTags): String? = song.albumArtist?.takeIf { it.isNotBlank() }
        ?: song.albumArtists?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** A featured artist is credited on the songs, not made an album artist of the album. */
    private fun taggedAlbumArtists(
        song: AlbumIdentityTags,
        explicit: String
    ): TaggedAlbumArtists {
        val listed = song.albumArtists.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val names = (listed.takeIf { it.size > 1 } ?: listOf(explicit)).map { name -> ArtistNames.splitFeaturing(name).ifEmpty { listOf(name.trim()) } }
        // One album artist featuring no one is the album's own, keyed exactly as the album is
        if (names.size == 1 && names.single().size == 1) return TaggedAlbumArtists(listOf(ArtistCredit(explicit, AlbumArtistGroupKey(artistKey(explicit)))), emptyList())
        return TaggedAlbumArtists(
            albumArtists = names.map { it.first() }.map { name -> ArtistCredit(name, AlbumArtistGroupKey(artistKey(name))) }.distinctBy { it.groupKey },
            featured = names.flatMap { it.drop(1) }
        )
    }

    /** The song's first credited artist, without whoever it features. */
    private fun primaryArtist(artists: List<String>): String? = artists.firstOrNull { it.isNotBlank() }?.let { artist -> FEATURING.split(artist).first().trim() }?.ifEmpty { null }

    /** The folder holding [path]: a file path's, or a document URI's, whose separators are encoded. */
    private fun folderOf(path: String): String {
        val slash = path.lastIndexOf('/')
        val encoded = path.lastIndexOf("%2F", ignoreCase = true)
        return path.substring(0, maxOf(slash, encoded, 0))
    }

    private const val MB = "mb:"
    private const val DIR = "dir:"

    private data class NameKey(
        val album: String?,
        val albumArtist: String?,
        val identity: String?
    )

    private class NameAlbum(
        album: String?,
        albumArtist: String?,
        identity: String?,
        val albumArtistName: String?,
        /** Whose album its album artist tags make it; null for an album without them, its album artist's alone. */
        val albumArtists: TaggedAlbumArtists? = null
    ) {
        val key = NameKey(album, albumArtist, identity)
    }

    private data class TaggedAlbumArtists(
        val albumArtists: List<ArtistCredit>,
        val featured: List<String>
    )

    private sealed interface AlbumBucket {
        data class ById(val id: String) : AlbumBucket

        data class ByName(val key: NameKey) : AlbumBucket
    }
}
