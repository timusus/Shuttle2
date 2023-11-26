package com.simplecityapps.playback.androidauto

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
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
    suspend fun getChildren(mediaId: String): List<MediaBrowserCompat.MediaItem> {
        return withContext(Dispatchers.IO) {
            when (val mediaIdWrapper: MediaIdWrapper? = parseMediaId(mediaId)) {
                is MediaIdWrapper.Directory.Root -> {
                    mutableListOf(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setTitle("Artists")
                                .setMediaId("media:/artist_root/")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        ),
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setTitle("Albums")
                                .setMediaId("media:/album_root/")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        ),
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setTitle("Playlists")
                                .setMediaId("media:/playlist_root/")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        ),
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setTitle("Shuffle All")
                                .setMediaId("media:/shuffle_all")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }

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
                    playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).firstOrNull().orEmpty().map { it.toMediaItem(mediaId) }
                }

                is MediaIdWrapper.Directory.Songs.Album -> {
                    songRepository
                        .getSongs(
                            SongQuery.AlbumGroupKeys(
                                listOf(
                                    SongQuery.AlbumGroupKey(
                                        key =
                                        AlbumGroupKey(
                                            key = mediaIdWrapper.albumGroupKey,
                                            albumArtistGroupKey = AlbumArtistGroupKey(mediaIdWrapper.albumArtistGroupKey)
                                        )
                                    )
                                )
                            )
                        )
                        .firstOrNull()
                        .orEmpty()
                        .map { it.toMediaItem(mediaId) }
                }

                is MediaIdWrapper.Directory.Songs.Playlist -> {
                    playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(mediaIdWrapper.playlistId)).firstOrNull()?.firstOrNull()?.let { playlist ->
                        playlistRepository.getSongsForPlaylist(playlist)
                            .firstOrNull()
                            .orEmpty()
                            .map { it.song.toMediaItem(mediaId) }
                    }.orEmpty()
                }

                else -> mutableListOf()
            }
        }
    }

    private fun AlbumArtist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name ?: friendlyArtistName)
                .setMediaId("${parentMediaId}artist/${groupKey.key}/albums/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Playlist.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("${parentMediaId}playlist/$id/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Album.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("${parentMediaId}artist/${groupKey?.albumArtistGroupKey?.key}/album/${groupKey?.key}/songs/")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun Song.toMediaItem(parentMediaId: String): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setTitle(name)
                .setMediaId("$parentMediaId$id")
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

                class Artist(val albumArtistGroupKey: String) : Albums()
            }

            object Playlists : Directory()

            sealed class Songs : Directory() {
                class Album(val albumGroupKey: String, val albumArtistGroupKey: String) : Songs()

                class Playlist(val playlistId: Long) : Songs()
            }
        }

        class Song(val songId: Long, val directory: Directory) : MediaIdWrapper()

        object ShuffleAll : MediaIdWrapper()
    }

    private fun parseMediaId(mediaId: String): MediaIdWrapper? {
        Timber.i("Parsing mediaId: $mediaId")
        return parsePathSegments(Uri.parse(mediaId).pathSegments)
    }

    private fun parsePathSegments(pathSegments: List<String>): MediaIdWrapper? {
        return when (pathSegments.last()) {
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
    }

    private fun List<String>.getNextSegment(segmentName: String): String? {
        val index = indexOf(segmentName)
        if (index >= 0 && size > index + 1) {
            return this[index + 1]
        }
        return null
    }

    suspend fun getPlayQueue(mediaId: String): PlayQueue? {
        return withContext(Dispatchers.IO) {
            when (val mediaIdWrapper = parseMediaId(mediaId)) {
                is MediaIdWrapper.Song -> {
                    when (mediaIdWrapper.directory) {
                        is MediaIdWrapper.Directory.Songs.Album -> {
                            val songs =
                                songRepository
                                    .getSongs(
                                        SongQuery.AlbumGroupKeys(
                                            listOf(
                                                SongQuery.AlbumGroupKey(
                                                    AlbumGroupKey(
                                                        key = mediaIdWrapper.directory.albumGroupKey,
                                                        albumArtistGroupKey = AlbumArtistGroupKey(mediaIdWrapper.directory.albumArtistGroupKey)
                                                    )
                                                )
                                            )
                                        )
                                    )
                                    .firstOrNull()
                                    .orEmpty()
                            PlayQueue(songs, songs.indexOfFirst { it.id == mediaIdWrapper.songId })
                        }

                        is MediaIdWrapper.Directory.Songs.Playlist -> {
                            val playlistSongs =
                                playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(mediaIdWrapper.directory.playlistId)).firstOrNull()?.firstOrNull()?.let { playlist ->
                                    playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
                                }.orEmpty()
                            PlayQueue(playlistSongs.map { it.song }, playlistSongs.indexOfFirst { it.song.id == mediaIdWrapper.songId })
                        }

                        else -> throw IllegalStateException("Cannot retrieve play queue for songId: ${mediaIdWrapper.songId}, directory: ${mediaIdWrapper.directory}")
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
    }
}

class PlayQueue(val songs: List<Song>, val position: Int)
