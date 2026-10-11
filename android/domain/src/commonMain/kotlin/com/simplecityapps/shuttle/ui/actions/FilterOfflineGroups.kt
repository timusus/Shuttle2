package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Offline mode's narrowing of album, album artist and genre lists: with it on, a group none of whose songs can play
 * offline (see [isAvailableOffline]) is hidden. With it off the lists pass through and nothing else is read.
 */
@Inject
class FilterOfflineGroups(
    private val offlineMode: OfflineModeSource,
    private val downloadStatuses: DownloadStatusSource,
    private val songRepository: SongRepository,
) {
    private class PlayableKeys(
        val albums: Set<AlbumGroupKey>,
        val albumArtists: Set<AlbumArtistGroupKey>,
        val genres: Set<String>,
    )

    fun albums(albums: Flow<List<Album>>): Flow<List<Album>> = narrow(albums) { album, keys -> album.groupKey?.let { it in keys.albums } ?: true }

    fun albumArtists(artists: Flow<List<AlbumArtist>>): Flow<List<AlbumArtist>> = narrow(artists) { artist, keys -> artist.groupKey in keys.albumArtists }

    fun genres(genres: Flow<List<Genre>>): Flow<List<Genre>> = narrow(genres) { genre, keys -> genre.name in keys.genres }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> narrow(groups: Flow<List<T>>, playable: (T, PlayableKeys) -> Boolean): Flow<List<T>> = offlineMode.observe().distinctUntilChanged().flatMapLatest { offline ->
        if (offline) {
            combine(groups, playableKeys()) { list, keys -> list.filter { playable(it, keys) } }
        } else {
            groups
        }
    }

    private fun playableKeys(): Flow<PlayableKeys> = combine(
        songRepository.getSongs(SongQuery.All()).filterNotNull(),
        downloadStatuses.observeDownloadedPaths(),
    ) { songs, paths ->
        val playable = songs.filter { it.isAvailableOffline(paths) }
        PlayableKeys(
            albums = playable.mapTo(HashSet()) { it.albumGroupKey },
            // A credited artist's list (Credited query) takes in the songs they're credited on, not only their own albums.
            albumArtists = playable.flatMapTo(HashSet()) { song -> song.albumArtistKeys + song.artistCredits.map { it.groupKey } },
            genres = playable.flatMapTo(HashSet()) { it.genres },
        )
    }
}
