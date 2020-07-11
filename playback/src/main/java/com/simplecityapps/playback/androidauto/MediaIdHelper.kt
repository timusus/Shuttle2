package com.simplecityapps.playback.androidauto

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaIdHelper @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) {

    suspend fun getChildren(mediaId: String): List<MediaBrowserCompat.MediaItem> {

        val mediaIdWrapper: MediaIdWrapper? = parseMediaId(mediaId)

        return when (mediaIdWrapper) {
            is MediaIdWrapper.Directory.Root -> {
                mutableListOf(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setTitle("Artists")
                            .setMediaId("media:/artists/")
                            .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    ),
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setTitle("Albums")
                            .setMediaId("media:/albums/")
                            .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    ), MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setTitle("Playlists")
                            .setMediaId("media:/playlists/")
                            .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
                )
            }
            is MediaIdWrapper.Directory.Artists -> {
                artistRepository.getAlbumArtists().firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }
            is MediaIdWrapper.Directory.Albums.All -> {
                albumRepository.getAlbums().firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }
            is MediaIdWrapper.Directory.Albums.Artist -> {
                albumRepository.getAlbums(AlbumQuery.AlbumArtist(mediaIdWrapper.artistName)).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }
            is MediaIdWrapper.Directory.Playlists -> {
                playlistRepository.getPlaylists().firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }
            is MediaIdWrapper.Directory.Songs.Album -> {
                songRepository
                    .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = mediaIdWrapper.albumName, albumArtistName = mediaIdWrapper.albumArtistName))))
                    .firstOrNull()
                    .orEmpty()
                    .map { it.toMediaItem(mediaId) }
            }
            is MediaIdWrapper.Directory.Songs.Playlist -> {
                playlistRepository.getSongsForPlaylist(mediaIdWrapper.playlistId).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
            }
            else -> mutableListOf()
        }
    }

    private fun AlbumArtist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${name}/albums/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Playlist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${id}/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Album.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${name}/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Song.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${id}")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    sealed class MediaIdWrapper {

        sealed class Directory : MediaIdWrapper() {

            object Root : Directory()

            object Artists : Directory()

            sealed class Albums : Directory() {
                object All : Albums()
                class Artist(val artistName: String) : Albums()
            }

            object Playlists : Directory()

            sealed class Songs : Directory() {
                class Album(val albumName: String, val albumArtistName: String) : Songs()
                class Playlist(val playlistId: Long) : Songs()
            }
        }

        class Song(val songId: Long, val directory: Directory) : MediaIdWrapper()
    }

    @Throws(IllegalStateException::class)
    fun parseMediaId(mediaId: String): MediaIdWrapper {
        return parsePathSegments(Uri.parse(mediaId).pathSegments)
    }

    @Throws(IllegalStateException::class)
    fun parsePathSegments(pathSegments: List<String>): MediaIdWrapper {
        return when (pathSegments.last()) {
            "root" -> MediaIdWrapper.Directory.Root
            "artists" -> MediaIdWrapper.Directory.Artists
            "albums" -> {
                if (pathSegments.contains("artists")) {
                    MediaIdWrapper.Directory.Albums.Artist(pathSegments.getNextSegment("artists")!!)
                } else {
                    MediaIdWrapper.Directory.Albums.All
                }
            }
            "songs" -> {
                when {
                    pathSegments.contains("albums") -> {
                        if (pathSegments.contains("artists")) {
                            MediaIdWrapper.Directory.Songs.Album(albumName = pathSegments.getNextSegment("albums")!!, albumArtistName = pathSegments.getNextSegment("artists")!!)
                        } else {
                            // Todo: Handle
                            throw IllegalStateException("artists missing from path segment")
                        }
                    }
                    pathSegments.contains("playlists") -> MediaIdWrapper.Directory.Songs.Playlist(pathSegments.getNextSegment("playlists")!!.toLong())
                    else -> throw IllegalStateException()
                }

            }
            "playlists" -> MediaIdWrapper.Directory.Playlists
            else -> {
                val directoryPath = pathSegments.toMutableList()
                directoryPath.removeAt(directoryPath.size - 1)
                MediaIdWrapper.Song(pathSegments.getNextSegment("songs")!!.toLong(), parsePathSegments(directoryPath) as MediaIdWrapper.Directory)
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

    suspend fun getPlayQueue(mediaId: String): PlayQueue {
        return withContext(Dispatchers.IO) {
            val mediaIdWrapper = parseMediaId(mediaId) as MediaIdWrapper.Song
            when (mediaIdWrapper.directory) {
                is MediaIdWrapper.Directory.Songs.Album -> {
                    val songs = songRepository
                        .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = mediaIdWrapper.directory.albumName, albumArtistName = mediaIdWrapper.directory.albumArtistName))))
                        .firstOrNull()
                        .orEmpty()
                    PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                }
                is MediaIdWrapper.Directory.Songs.Playlist -> {
                    val songs = playlistRepository.getSongsForPlaylist(mediaIdWrapper.directory.playlistId).firstOrNull().orEmpty()
                    PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                }
                else -> throw IllegalStateException("Cannot retrieve play queue for songId: ${mediaIdWrapper.songId}, directory: ${mediaIdWrapper.directory}")
            }
        }
    }
}

class PlayQueue(val songs: List<Song>, val playbackPosition: Int)