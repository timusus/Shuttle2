package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.firstOrNull

/** The album's songs, which stand in for the album when a source only knows how to find artwork for a song. */
internal suspend fun SongRepository.songsOf(album: Album): List<Song> = getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
    .firstOrNull()
    .orEmpty()

internal suspend fun SongRepository.firstSongOf(album: Album): Song? = songsOf(album).firstOrNull()

internal suspend fun SongRepository.firstSongOf(albumArtist: AlbumArtist): Song? = getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
    .firstOrNull()
    ?.firstOrNull()
