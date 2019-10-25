package com.simplecityapps.playback.androidauto

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import io.reactivex.Single
import timber.log.Timber
import javax.inject.Inject

class MediaIdHelper @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) {

    fun getChildren(mediaId: String): Single<List<MediaBrowserCompat.MediaItem>> {
        val mediaIdWrapper: MediaIdWrapper? = try {
            parseMediaId(mediaId)
        } catch (e: IllegalStateException) {
            Timber.e("Failed to parse media id: ${e.localizedMessage}")
            return Single.error(e)
        }

        return when (mediaIdWrapper) {
            is MediaIdWrapper.Directory.Root -> {
                Single.just(
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
                )
            }
            is MediaIdWrapper.Directory.Artists -> {
                artistRepository.getAlbumArtists().first(emptyList()).map { albumArtists ->
                    albumArtists.map { it.toMediaItem(mediaId) }
                }
            }
            is MediaIdWrapper.Directory.Albums.All -> {
                albumRepository.getAlbums().first(emptyList()).map { albums ->
                    albums.map { it.toMediaItem(mediaId) }
                }
            }
            is MediaIdWrapper.Directory.Albums.Artist -> {
                albumRepository.getAlbums(AlbumQuery.AlbumArtistId(mediaIdWrapper.artistId)).first(emptyList()).map { albums ->
                    albums.map { it.toMediaItem(mediaId) }
                }
            }
            is MediaIdWrapper.Directory.Playlists -> {
                playlistRepository.getPlaylists().first(emptyList()).map { playlists ->
                    playlists.map { it.toMediaItem(mediaId) }
                }
            }
            is MediaIdWrapper.Directory.Songs.Album -> {
                songRepository.getSongs(SongQuery.AlbumIds(listOf(mediaIdWrapper.albumId))).first(emptyList()).map { songs ->
                    songs.map { it.toMediaItem(mediaId) }
                }
            }
            is MediaIdWrapper.Directory.Songs.Playlist -> {
                playlistRepository.getSongsForPlaylist(mediaIdWrapper.playlistId).first(emptyList()).map { songs ->
                    songs.map { it.toMediaItem(mediaId) }
                }
            }
            else -> Single.just(mutableListOf())
        }
    }

    private fun AlbumArtist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            android.support.v4.media.MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${id}/albums/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Playlist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            android.support.v4.media.MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${id}/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Album.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            android.support.v4.media.MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId${id}/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Song.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            android.support.v4.media.MediaDescriptionCompat.Builder()
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
                class Artist(val artistId: Long) : Albums()
            }

            object Playlists : Directory()

            sealed class Songs : Directory() {
                class Album(val albumId: Long) : Songs()
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
                    MediaIdWrapper.Directory.Albums.Artist(pathSegments.getNextSegment("artists")!!.toLong())
                } else {
                    MediaIdWrapper.Directory.Albums.All
                }
            }
            "songs" -> {
                when {
                    pathSegments.contains("albums") -> MediaIdWrapper.Directory.Songs.Album(pathSegments.getNextSegment("albums")!!.toLong())
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

    fun getPlayQueue(mediaId: String): Single<PlayQueue> {
        val mediaIdWrapper = parseMediaId(mediaId) as MediaIdWrapper.Song

        return when (mediaIdWrapper.directory) {
            is MediaIdWrapper.Directory.Songs.Album -> {
                songRepository.getSongs(SongQuery.AlbumIds(listOf(mediaIdWrapper.directory.albumId))).first(emptyList()).map { songs ->
                    PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                }
            }
            is MediaIdWrapper.Directory.Songs.Playlist -> {
                playlistRepository.getSongsForPlaylist(mediaIdWrapper.directory.playlistId).first(emptyList()).map { songs ->
                    PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                }
            }
            else -> throw IllegalStateException("Cannot retrieve play queue for songId: ${mediaIdWrapper.songId}, directory: ${mediaIdWrapper.directory}")
        }
    }
}

class PlayQueue(val songs: List<Song>, val playbackPosition: Int)