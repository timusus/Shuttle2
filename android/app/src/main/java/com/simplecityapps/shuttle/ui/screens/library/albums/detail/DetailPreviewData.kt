package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.removeArticles

/**
 * Sample models for the album and album artist detail `@Preview`s.
 *
 * Deterministic by construction — the previews render with `LocalInspectionMode` on, so artwork
 * never loads. Album group keys are taken from the songs themselves, so the album artist screen
 * matches songs to albums the same way real data does.
 */
private const val PREVIEW_ARTIST_NAME = "The Velvet Static"

private fun previewSong(
    id: Long,
    name: String,
    album: String,
    track: Int,
    duration: Int,
): Song = Song(
    id = id,
    name = name,
    albumArtist = PREVIEW_ARTIST_NAME,
    artists = listOf(PREVIEW_ARTIST_NAME),
    album = album,
    track = track,
    disc = 1,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = "/music/preview/$id.flac",
    size = 1,
    mimeType = "audio/flac",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = MediaProviderType.Shuttle,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
)

private fun previewAlbumOf(
    songs: List<Song>,
    year: Int,
): Album = Album(
    name = songs.first().album,
    albumArtist = PREVIEW_ARTIST_NAME,
    artists = listOf(PREVIEW_ARTIST_NAME),
    songCount = songs.size,
    duration = songs.sumOf { it.duration },
    year = year,
    playCount = 0,
    lastSongPlayed = null,
    lastSongCompleted = null,
    groupKey = songs.first().albumGroupKey,
    mediaProviders = listOf(MediaProviderType.Shuttle),
)

internal val previewAlbumSongs: List<Song> = listOf(
    previewSong(id = 1, name = "Harbour Lights", album = "Low Tide Sessions", track = 1, duration = 147_000),
    previewSong(id = 2, name = "Slow Arrivals", album = "Low Tide Sessions", track = 2, duration = 201_000),
    previewSong(id = 3, name = "Undertow", album = "Low Tide Sessions", track = 3, duration = 168_000),
    previewSong(id = 4, name = "Low Tide", album = "Low Tide Sessions", track = 4, duration = 166_000),
)

internal val previewAlbum: Album = previewAlbumOf(previewAlbumSongs, year = 2021)

private val previewLiveAlbumSongs: List<Song> = listOf(
    previewSong(id = 10, name = "Afterglow", album = "Afterglow Live", track = 1, duration = 189_000),
    previewSong(id = 11, name = "Nightjar", album = "Afterglow Live", track = 2, duration = 164_000),
    previewSong(id = 12, name = "Static Bloom", album = "Afterglow Live", track = 3, duration = 167_000),
)

internal val previewLiveAlbum: Album = previewAlbumOf(previewLiveAlbumSongs, year = 2023)

internal val previewArtistAlbums: List<Album> = listOf(previewLiveAlbum, previewAlbum)

internal val previewArtistSongs: List<Song> = previewLiveAlbumSongs + previewAlbumSongs

internal val previewAlbumArtist: AlbumArtist = AlbumArtist(
    name = PREVIEW_ARTIST_NAME,
    artists = listOf(PREVIEW_ARTIST_NAME),
    albumCount = previewArtistAlbums.size,
    songCount = previewArtistSongs.size,
    playCount = 0,
    groupKey = AlbumArtistGroupKey(PREVIEW_ARTIST_NAME.lowercase().removeArticles()),
    mediaProviders = listOf(MediaProviderType.Shuttle),
)
