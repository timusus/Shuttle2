package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class MoreBy(val albums: List<Album>, val names: List<String>)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeMoreBy(
    album: Flow<Album?>,
    groupKey: AlbumGroupKey?,
    observeAlbums: ObserveAlbums,
    observeArtists: ObserveArtists,
): Flow<MoreBy> {
    /**
     * Its album artists' own albums but this one, newest first: each of an album of several, and A's of "A feat. B". Starts
     * empty so the Ready state never waits on it; it is empty for an album with no album artist, and for a compilation,
     * whose "Various Artists" is nobody's catalogue.
     */
    val moreByArtistKeys: Flow<Set<AlbumArtistGroupKey>> = album
        .map { album -> album?.albumArtistKeys.orEmpty().filterTo(LinkedHashSet()) { it.key != null && it.key != AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS) } }
        .distinctUntilChanged()

    val moreByArtist: Flow<List<Album>> = moreByArtistKeys
        .flatMapLatest { artistKeys ->
            if (artistKeys.isEmpty()) {
                flowOf(emptyList())
            } else {
                observeAlbums(AlbumQuery.ArtistGroupKeys(artistKeys))
                    .map { albums -> albums.filter { it.groupKey != groupKey }.sortedWith(ArtistSongComparator.albumNewest) }
            }
        }
        .onStart { emit(emptyList()) }

    /** The names of [moreByArtistKeys], in their order, so the shelf is titled from the artists it lists, not from the album's raw tag. */
    val moreByArtistNames: Flow<List<String>> = moreByArtistKeys
        .flatMapLatest { artistKeys ->
            if (artistKeys.isEmpty()) {
                flowOf(emptyList())
            } else {
                observeArtists(AlbumArtistQuery.Credited()).map { artists ->
                    val namesByKey = artists.associate { it.groupKey to it.name }
                    artistKeys.mapNotNull { namesByKey[it]?.takeIf { name -> name.isNotBlank() } }
                }
            }
        }
        .distinctUntilChanged()
        .onStart { emit(emptyList()) }

    return combine(moreByArtist, moreByArtistNames, ::MoreBy)
}
