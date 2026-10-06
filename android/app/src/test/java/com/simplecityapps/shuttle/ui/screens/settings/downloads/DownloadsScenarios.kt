package com.simplecityapps.shuttle.ui.screens.settings.downloads

import com.simplecityapps.createSong

private const val MEGABYTE = 1_000_000L

fun downloadedAlbum(
    name: String = "Night Drive",
    artist: String = "Oda Kestrel",
    songCount: Int = 10,
    bytes: Long = 80 * MEGABYTE
) = DownloadedAlbum(
    key = "$artist/$name",
    name = name,
    artist = artist,
    songCount = songCount,
    bytes = bytes,
    cover = createSong(album = name)
)

fun readyDownloads(
    albums: List<DownloadedAlbum> = listOf(downloadedAlbum(), downloadedAlbum(name = "Low Tide", songCount = 1, bytes = 8 * MEGABYTE)),
    storageBytes: Long = albums.sumOf { it.bytes }
) = DownloadsUiState(albums = albums, storageBytes = storageBytes, loading = false)

fun emptyDownloads() = DownloadsUiState(loading = false)

val loadingDownloads = DownloadsUiState()
