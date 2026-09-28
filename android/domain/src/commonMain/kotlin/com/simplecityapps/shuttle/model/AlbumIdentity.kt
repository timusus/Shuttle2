package com.simplecityapps.shuttle.model

/** The tags [AlbumIdentityRule] reads from a song: a [Song]'s ([Song.identityTags]), or a database row's. */
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
    val path: String
)

/** The album a song belongs to ([groupKey]) and the album artist that album shows ([albumArtistName]). */
data class AlbumIdentity(
    val groupKey: AlbumGroupKey,
    val albumArtistName: String?
) {
    val albumArtistGroupKey: AlbumArtistGroupKey get() = groupKey.albumArtistGroupKey ?: AlbumArtistGroupKey(null)
}

/**
 * The one album identity rule (#637): everything that groups songs into albums or album artists goes through [resolve].
 * An album is decided over its songs, not one song at a time, so the rule reads a whole library at once.
 *
 * 1. The name rule makes the albums: the album name plus its album artist, which is
 *    - the album artist tag (ALBUMARTIST, else ALBUMARTISTS joined);
 *    - else "Various Artists" when the song is tagged a compilation;
 *    - else, among the untagged songs of that name in one folder, their artist when every one agrees (the first
 *      credited artist, before any "feat."), so an album spread over disc folders stays one;
 *    - else "Various Artists", told apart from others of that name by the folder.
 *    A song with no album name is its own artist's, as before: songs without one aren't gathered into one album.
 * 2. Ids then key each of those albums, per album rather than per song: when every song has a MusicBrainz release id the
 *    album is keyed by it (so different releases of one name split, and the same release under differing tags joins);
 *    else when every song has a server album id, by that, scoped to its source. One untagged song keeps its album on
 *    the name rule, so a partly tagged album never splits.
 * 3. Songs one id gathers from several name albums take the name and album artist of the largest of them.
 */
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
                explicit != null -> NameAlbum(albumKey(song.album), artistKey(explicit), null, explicit)
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
                val displayNames = names.filter { it.key == largest }.mapNotNull { it.albumArtistName }
                val albumArtistName = displayNames.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val identity = AlbumIdentity(
                    groupKey = AlbumGroupKey(largest.album, AlbumArtistGroupKey(largest.albumArtist), (bucket as? AlbumBucket.ById)?.id ?: largest.identity),
                    albumArtistName = albumArtistName
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
        val albumArtistName: String?
    ) {
        val key = NameKey(album, albumArtist, identity)
    }

    private sealed interface AlbumBucket {
        data class ById(val id: String) : AlbumBucket

        data class ByName(val key: NameKey) : AlbumBucket
    }
}

/** The tags [AlbumIdentityRule] reads from this song. */
val Song.identityTags: AlbumIdentityTags
    get() = AlbumIdentityTags(
        songId = id,
        album = album,
        albumArtist = albumArtist,
        albumArtists = albumArtists,
        artists = artists,
        compilation = compilation,
        mbAlbumId = mbAlbumId,
        serverAlbumId = serverAlbumId,
        mediaProvider = mediaProvider,
        path = path
    )

/** These songs, each holding the album identity [AlbumIdentityRule] gives it among them: a whole library's songs. */
fun List<Song>.withAlbumIdentities(): List<Song> {
    // Resolved by position, not id: songs not yet stored all have id 0
    val identities = AlbumIdentityRule.resolve(mapIndexed { index, song -> song.identityTags.copy(songId = index.toLong()) })
    return mapIndexed { index, song -> song.copy(albumIdentity = identities.getValue(index.toLong())) }
}

/** These songs, each holding its identity in [identities] (the library's, resolved already); one not in it keeps its own. */
fun List<Song>.withAlbumIdentities(identities: Map<Long, AlbumIdentity>): List<Song> = map { song -> identities[song.id]?.let { song.copy(albumIdentity = it) } ?: song }
