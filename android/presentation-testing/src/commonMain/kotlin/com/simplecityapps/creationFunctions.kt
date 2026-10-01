package com.simplecityapps

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.platform.PlatformFeatures
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

fun createSong(
    id: Long = 1,
    name: String = "song-name",
    albumArtist: String = "album-artist",
    album: String = "album-name",
    track: Int = 1,
    disc: Int = 1,
    duration: Int = 1,
    date: LocalDate = LocalDate(2024, 2, 11),
    playCount: Int = 0,
    lastPlayed: Instant? = Instant.fromEpochSeconds(1),
    lastCompleted: Instant? = Instant.fromEpochSeconds(1),
    mediaProvider: MediaProviderType = MediaProviderType.Shuttle,
    grouping: String? = null,
    path: String = "/path/to/song",
    artists: List<String> = emptyList(),
) = Song(
    id = id,
    name = name,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    track = track,
    disc = disc,
    duration = duration,
    date = date,
    genres = emptyList(),
    path = path,
    size = 1,
    mimeType = "ogg",
    lastModified = Instant.fromEpochSeconds(1),
    lastPlayed = lastPlayed,
    lastCompleted = lastCompleted,
    playCount = playCount,
    playbackPosition = 1,
    blacklisted = false,
    externalId = null,
    mediaProvider = mediaProvider,
    replayGainTrack = null,
    replayGainAlbum = null,
    lyrics = null,
    grouping = grouping,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
)

fun createAlbumArtist(
    name: String = "artist-name",
    artists: List<String> = listOf(name),
    albumCount: Int = 1,
    songCount: Int = 5,
    playCount: Int = 0,
    groupKey: AlbumArtistGroupKey = AlbumArtistGroupKey(name),
    mediaProviders: List<MediaProviderType> = listOf(MediaProviderType.Shuttle),
    appearsOnCount: Int = 0,
) = AlbumArtist(
    name = name,
    artists = artists,
    albumCount = albumCount,
    songCount = songCount,
    playCount = playCount,
    groupKey = groupKey,
    mediaProviders = mediaProviders,
    appearsOnCount = appearsOnCount,
)

fun createAlbum(
    name: String = "album-name",
    albumArtist: String? = "album-artist",
    artists: List<String> = listOf(albumArtist ?: "album-artist"),
    songCount: Int = 10,
    duration: Int = 600,
    year: Int? = 2024,
    playCount: Int = 0,
    groupKey: AlbumGroupKey? = AlbumGroupKey(name, AlbumArtistGroupKey(albumArtist)),
    mediaProviders: List<MediaProviderType> = listOf(MediaProviderType.Shuttle),
    dateAdded: Instant? = null,
) = Album(
    name = name,
    albumArtist = albumArtist,
    artists = artists,
    songCount = songCount,
    duration = duration,
    year = year,
    playCount = playCount,
    lastSongPlayed = null,
    lastSongCompleted = null,
    groupKey = groupKey,
    mediaProviders = mediaProviders,
    dateAdded = dateAdded,
)

fun createGenre(
    name: String = "Rock",
    songCount: Int = 10,
    duration: Int = 600,
    mediaProviders: List<MediaProviderType> = listOf(MediaProviderType.Shuttle),
) = Genre(
    name = name,
    songCount = songCount,
    duration = duration,
    mediaProviders = mediaProviders,
)

fun createPlaylist(
    id: Long = 1,
    name: String = "My Playlist",
    songCount: Int = 5,
    duration: Int = 300,
    sortOrder: PlaylistSongSortOrder = PlaylistSongSortOrder.Position,
    sortDescending: Boolean = false,
    mediaProvider: MediaProviderType = MediaProviderType.Shuttle,
    externalId: String? = null,
) = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    duration = duration,
    sortOrder = sortOrder,
    sortDescending = sortDescending,
    mediaProvider = mediaProvider,
    externalId = externalId,
)

fun createAudioFile(
    title: String? = "Title",
    artists: List<String> = listOf("Artist"),
    album: String? = "Album",
    albumArtist: String? = "Album Artist",
    year: String? = "2024",
    track: Int? = 1,
    trackTotal: Int? = 10,
    disc: Int? = 1,
    discTotal: Int? = 1,
    genres: List<String> = listOf("Jazz"),
    lyrics: String? = null,
) = AudioFile(
    path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fsong.mp3",
    size = 1,
    lastModified = 0,
    mimeType = "audio/mpeg",
    title = title,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    track = track,
    trackTotal = trackTotal,
    disc = disc,
    discTotal = discTotal,
    duration = 1000,
    year = year,
    genres = genres,
    replayGainTrack = null,
    replayGainAlbum = null,
    lyrics = lyrics,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
)

fun createPlatformFeatures(
    homeScreenWidgets: Boolean = true,
    artworkPrefetch: Boolean = true,
    scheduledRescan: Boolean = true,
    cast: Boolean = true,
    offlineDownloads: Boolean = true,
) = PlatformFeatures(
    homeScreenWidgets = homeScreenWidgets,
    artworkPrefetch = artworkPrefetch,
    scheduledRescan = scheduledRescan,
    cast = cast,
    offlineDownloads = offlineDownloads,
)
