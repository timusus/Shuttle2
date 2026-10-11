package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * The songs matching [SongQuery], the whole library by default; re-emits as they change. Emits
 * nothing until the library has loaded, rather than the repository's interim null. With Offline mode on, server songs
 * that aren't downloaded are left out unless [includeUnavailable]: for screens that manage the library, not browse it.
 */
@Inject
class ObserveSongs(
    private val songRepository: SongRepository,
    private val filterOfflineSongs: FilterOfflineSongs,
) {
    operator fun invoke(
        query: SongQuery = SongQuery.All(),
        includeUnavailable: Boolean = false,
    ): Flow<List<Song>> {
        val songs = songRepository.getSongs(query).filterNotNull()
        return if (includeUnavailable) songs else filterOfflineSongs(songs)
    }
}
