package com.simplecityapps.playback.androidauto

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.toMediaMetadata
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaIdHelper
@Inject
constructor(
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) {
    /** The children of the browsable item [mediaId]: empty for an id that isn't one. */
    suspend fun getChildren(mediaId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        when (val mediaIdWrapper: MediaIdWrapper? = parseMediaId(mediaId)) {
            is MediaIdWrapper.Directory.Root -> rootChildren

            is MediaIdWrapper.Directory.Artists -> {
                artistRepository.getAlbumArtists(AlbumArtistQuery.All()).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Albums.All -> {
                albumRepository.getAlbums(AlbumQuery.All()).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Albums.Artist -> {
                albumRepository.getAlbums(
                    AlbumQuery.ArtistGroupKey(
                        AlbumArtistGroupKey(
                            mediaIdWrapper.albumArtistGroupKey
                        )
                    )
                ).firstOrNull().orEmpty()
                    .map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Playlists -> {
                listOf(favourites) + playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Songs.Favourites -> {
                favouriteSongs().map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Songs.Album -> {
                songsForAlbum(mediaIdWrapper).map { it.toMediaItem(mediaId) }
            }

            is MediaIdWrapper.Directory.Songs.Playlist -> {
                songsForPlaylist(mediaIdWrapper).map { it.toMediaItem(mediaId) }
            }

            else -> emptyList()
        }
    }

    /**
     * The item [mediaId] names: the root, one of its folders or Shuffle All, or a song. An artist, album or playlist
     * folder isn't looked up, so it's returned bare, with no title; null for an id that names nothing.
     */
    suspend fun getItem(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        when (val mediaIdWrapper = parseMediaId(mediaId)) {
            is MediaIdWrapper.Directory.Root -> root

            is MediaIdWrapper.Directory.Artists, MediaIdWrapper.Directory.Albums.All, MediaIdWrapper.Directory.Playlists -> rootChildren.firstOrNull { it.mediaId == mediaId }

            is MediaIdWrapper.ShuffleAll -> rootChildren.firstOrNull { it.mediaId == SHUFFLE_ALL_ID }

            is MediaIdWrapper.Directory.Songs.Favourites -> favourites

            is MediaIdWrapper.Directory -> browsableItem(mediaId, title = null)

            is MediaIdWrapper.Song -> songRepository.getSongs(SongQuery.SongIds(listOf(mediaIdWrapper.songId))).firstOrNull()?.firstOrNull()?.let { song ->
                song.toMediaItem(mediaId.removeSuffix(mediaIdWrapper.songId.toString()))
            }

            null -> null
        }
    }

    /** The songs matching [query], as playable items whose media ids are the songs' own ids. */
    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        songRepository.getSongs(SongQuery.Search(query = query)).firstOrNull().orEmpty().map { it.toMediaItem(parentMediaId = "") }
    }

    private suspend fun songsForAlbum(directory: MediaIdWrapper.Directory.Songs.Album): List<Song> = songRepository
        .getSongs(
            SongQuery.AlbumGroupKeys(
                listOf(
                    SongQuery.AlbumGroupKey(
                        key =
                            AlbumGroupKey(
                                key = directory.albumGroupKey,
                                albumArtistGroupKey = AlbumArtistGroupKey(directory.albumArtistGroupKey)
                            )
                    )
                )
            )
        )
        .firstOrNull()
        .orEmpty()

    private suspend fun songsForPlaylist(directory: MediaIdWrapper.Directory.Songs.Playlist): List<Song> = playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(directory.playlistId)).firstOrNull()?.firstOrNull()?.let { playlist ->
        playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
    }.orEmpty()

    private suspend fun favouriteSongs(): List<Song> = songRepository.getSongs(SongQuery.Favourites).firstOrNull().orEmpty()

    private fun AlbumArtist.toMediaItem(parentMediaId: String): MediaItem = browsableItem("${parentMediaId}artist/${groupKey.key}/albums/", name ?: friendlyArtistName, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)

    private fun Playlist.toMediaItem(parentMediaId: String): MediaItem = browsableItem("${parentMediaId}playlist/$id/songs/", name, MediaMetadata.MEDIA_TYPE_PLAYLIST)

    private fun Album.toMediaItem(parentMediaId: String): MediaItem = browsableItem("${parentMediaId}artist/${groupKey?.albumArtistGroupKey?.key}/album/${groupKey?.key}/songs/", name, MediaMetadata.MEDIA_TYPE_ALBUM)

    private fun Song.toMediaItem(parentMediaId: String): MediaItem = MediaItem.Builder()
        .setMediaId("$parentMediaId$id")
        .setMediaMetadata(toMediaMetadata())
        .build()

    sealed class MediaIdWrapper {
        sealed class Directory : MediaIdWrapper() {
            object Root : Directory()

            object Artists : Directory()

            sealed class Albums : Directory() {
                object All : Albums()

                class Artist(val albumArtistGroupKey: String) : Albums()
            }

            object Playlists : Directory()

            sealed class Songs : Directory() {
                class Album(val albumGroupKey: String, val albumArtistGroupKey: String) : Songs()

                class Playlist(val playlistId: Long) : Songs()

                /** The Favourites smart playlist: a flag on each song rather than a stored playlist (#497). */
                object Favourites : Songs()
            }
        }

        /** A song, in the folder it was browsed from, or none for one found by search. */
        class Song(val songId: Long, val directory: Directory?) : MediaIdWrapper()

        object ShuffleAll : MediaIdWrapper()
    }

    private fun parseMediaId(mediaId: String): MediaIdWrapper? {
        Timber.i("Parsing mediaId: $mediaId")
        return parsePathSegments(Uri.parse(mediaId).pathSegments)
    }

    private fun parsePathSegments(pathSegments: List<String>): MediaIdWrapper? = when (pathSegments.lastOrNull()) {
        null -> null

        // A search result's id is the song's own.
        pathSegments.singleOrNull()?.takeIf { it.toLongOrNull() != null } -> MediaIdWrapper.Song(pathSegments.single().toLong(), directory = null)

        "root" -> MediaIdWrapper.Directory.Root

        "artist_root" -> MediaIdWrapper.Directory.Artists

        "album_root" -> MediaIdWrapper.Directory.Albums.All

        "playlist_root" -> MediaIdWrapper.Directory.Playlists

        "shuffle_all" -> MediaIdWrapper.ShuffleAll

        "albums" -> {
            if (pathSegments.contains("artist")) {
                MediaIdWrapper.Directory.Albums.Artist(pathSegments.getNextSegment("artist")!!)
            } else {
                MediaIdWrapper.Directory.Albums.All
            }
        }

        "songs" -> {
            when {
                pathSegments.contains("album") -> {
                    MediaIdWrapper.Directory.Songs.Album(
                        albumGroupKey = pathSegments.getNextSegment("album")!!,
                        albumArtistGroupKey = pathSegments.getNextSegment("artist")!!
                    )
                }

                pathSegments.contains(FAVOURITES_SEGMENT) -> MediaIdWrapper.Directory.Songs.Favourites

                pathSegments.contains("playlist") -> MediaIdWrapper.Directory.Songs.Playlist(pathSegments.getNextSegment("playlist")!!.toLong())

                else -> throw IllegalStateException()
            }
        }

        else -> {
            val directoryPath = pathSegments.toMutableList()
            directoryPath.removeAt(directoryPath.size - 1)
            pathSegments.getNextSegment("songs")?.let { thing ->
                MediaIdWrapper.Song(thing.toLong(), parsePathSegments(directoryPath) as MediaIdWrapper.Directory)
            } ?: run {
                Timber.e("Failed to parse path segments: ${pathSegments.joinToString("/") { it }}")
                null
            }
        }
    }

    private fun List<String>.getNextSegment(segmentName: String): String? {
        val index = indexOf(segmentName)
        if (index >= 0 && size > index + 1) {
            return this[index + 1]
        }
        return null
    }

    /** The songs to play for the playable item [mediaId], and where in them to start; null for an id that isn't one. */
    suspend fun getPlayQueue(mediaId: String): PlayQueue? = withContext(Dispatchers.IO) {
        when (val mediaIdWrapper = parseMediaId(mediaId)) {
            is MediaIdWrapper.Song -> {
                when (val directory = mediaIdWrapper.directory) {
                    is MediaIdWrapper.Directory.Songs.Album -> {
                        val songs = songsForAlbum(directory)
                        PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                    }

                    is MediaIdWrapper.Directory.Songs.Playlist -> {
                        val songs = songsForPlaylist(directory)
                        PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                    }

                    is MediaIdWrapper.Directory.Songs.Favourites -> {
                        val songs = favouriteSongs()
                        PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                    }

                    // A search result: the song on its own.
                    null -> songRepository.getSongs(SongQuery.SongIds(listOf(mediaIdWrapper.songId))).firstOrNull()?.takeIf { it.isNotEmpty() }?.let { songs -> PlayQueue(songs, 0) }

                    else -> throw IllegalStateException("Cannot retrieve play queue for songId: ${mediaIdWrapper.songId}, directory: $directory")
                }
            }

            is MediaIdWrapper.ShuffleAll -> {
                val songs =
                    songRepository
                        .getSongs(SongQuery.All())
                        .firstOrNull()
                        .orEmpty()
                PlayQueue(songs.shuffled(), 0)
            }

            else -> {
                null
            }
        }
    }

    companion object {
        const val ROOT_ID = "media:/root/"
        const val SHUFFLE_ALL_ID = "media:/shuffle_all"
        private const val FAVOURITES_SEGMENT = "favourites"
        const val FAVOURITES_ID = "media:/playlist_root/$FAVOURITES_SEGMENT/songs/"

        val root: MediaItem = browsableItem(ROOT_ID, title = null, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

        private val rootChildren: List<MediaItem> = listOf(
            browsableItem("media:/artist_root/", "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            browsableItem("media:/album_root/", "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            browsableItem("media:/playlist_root/", "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            MediaItem.Builder()
                .setMediaId(SHUFFLE_ALL_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Shuffle All")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        /** Listed first among the playlists, where the Favorites playlist used to be. */
        private val favourites: MediaItem = browsableItem(FAVOURITES_ID, "Favorites", MediaMetadata.MEDIA_TYPE_PLAYLIST)

        private fun browsableItem(mediaId: String, title: String?, mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }
}

class PlayQueue(val songs: List<Song>, val position: Int)
